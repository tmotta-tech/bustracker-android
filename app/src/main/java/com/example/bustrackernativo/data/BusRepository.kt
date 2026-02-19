package com.example.bustrackernativo.data

import android.content.Context
import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

class BusRepository private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: BusRepository? = null
        
        // Cache duration for Rio API data (30 seconds for fresh, 5 min for stale)
        private const val CACHE_DURATION_MS = 30_000L
        private const val STALE_CACHE_DURATION_MS = 300_000L  // 5 minutes - show stale data while refreshing
        
        // Disk cache file names
        private const val RIO_CACHE_FILE = "rio_bus_cache.json"
        private const val DETRO_CACHE_FILE = "detro_bus_cache.json"

        fun getInstance(context: Context): BusRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BusRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // Disk cache directory
    private val cacheDir: File by lazy { File(context.cacheDir, "bus_data") }

    private val gson = GsonBuilder().setLenient().create()
    private var database: BusDatabase? = null
    
    // In-memory cache for Rio API (faster subsequent loads)
    private var rioCacheData: List<BusInfo> = emptyList()
    private var rioCacheTime: Long = 0L
    private var rioCacheLines: Set<String> = emptySet()
    
    // In-memory cache for DETRO API (faster subsequent loads)
    private var detroCacheData: List<BusInfo> = emptyList()
    private var detroCacheTime: Long = 0L
    private var detroCacheLines: Set<String> = emptySet()
    
    // Official route shapes from GTFS (loaded once, bundled with APK)
    private var routeShapes: Map<String, List<List<Double>>>? = null
    
    // Optimized OkHttpClient for large API responses
    // - OkHttp automatically handles GZIP compression/decompression
    // - HTTP/2 is enabled by default for multiplexing
    // - Extended read timeout for 230K+ buses payload (~10MB)
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)   // Fast failure on bad connection
        .readTimeout(15, TimeUnit.SECONDS)     // Reduced: Worker already processes data
        .writeTimeout(10, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(10, 60, TimeUnit.SECONDS)) // More aggressive pooling
        .retryOnConnectionFailure(true)
        .build()
    
    // Scoped coroutine for background tasks
    private val backgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val detroApi = Retrofit.Builder()
        // Default: https://appbus.exall-host.com.br/
        // Optimized: https://api.mobilidade.rio/ (requires API_CHANGES.md applied)
        .baseUrl("https://appbus.exall-host.com.br/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(BusService::class.java)

    private val rioApi = Retrofit.Builder()
        // Using Cloudflare Worker proxy for FAST server-side filtering
        // Proxy fetches all buses once, caches 30s, returns only matching lines
        .baseUrl("https://busapp.thiago-info-2c9.workers.dev/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(BusService::class.java)

    suspend fun getDatabase(): BusDatabase? {
        if (database == null) loadDatabase()
        return database
    }

    suspend fun loadDatabase() {
        if (database != null) return
        withContext(Dispatchers.IO) {
            try {
                Log.d("BusRepository", "Loading bus_database.json from assets...")
                val json = context.assets.open("bus_database.json").bufferedReader().use { it.readText() }
                database = gson.fromJson(json, BusDatabase::class.java)
                Log.d("BusRepository", "Database loaded! Detro: ${database?.detro?.size}, Rio: ${database?.rio?.size}")
            } catch (e: Exception) {
                Log.e("BusRepository", "Error loading database", e)
                e.printStackTrace()
            }
        }
    }
    
    // ============================================
    // GLOBAL PREFETCH (Load once, instant searches)
    // ============================================
    
    // Store ALL Rio buses in memory after first fetch - subsequent searches are INSTANT
    private var globalRioCache: List<RioBusResponse> = emptyList()
    private var globalRioCacheTime: Long = 0L
    private var isGlobalPrefetchInProgress = false
    
    /**
     * Warm the Cloudflare Worker cache on app startup.
     * Makes a lightweight request to trigger the Worker to fetch from the API
     * and cache the data (15s). Next actual search will be instant.
     */
    suspend fun prefetchAllRioBuses() {
        if (isGlobalPrefetchInProgress) return
        
        isGlobalPrefetchInProgress = true
        withContext(Dispatchers.IO) {
            try {
                Log.d("BusRepository", "Warming Worker cache...")
                val start = System.currentTimeMillis()
                // Request a common line to warm the cache
                // Worker will fetch ALL data from API and cache it
                val response = rioApi.getRioBuses(linha = "485")
                val elapsed = System.currentTimeMillis() - start
                if (response.isSuccessful) {
                    Log.d("BusRepository", "Cache warm complete in ${elapsed}ms")
                } else {
                    Log.d("BusRepository", "Cache warm: ${response.code()} in ${elapsed}ms")
                }
            } catch (e: Exception) {
                Log.e("BusRepository", "Cache warm failed: ${e.message}")
            } finally {
                isGlobalPrefetchInProgress = false
            }
        }
    }
    
    /**
     * Get Rio buses for specific lines from global cache (INSTANT!)
     * Falls back to API call if cache is empty
     */
    private fun filterRioBusesFromGlobalCache(targets: List<String>): List<BusInfo> {
        if (globalRioCache.isEmpty()) return emptyList()
        
        return globalRioCache.filter { bus ->
            val rawLine = bus.linha?.replace(".0", "")?.trim() ?: ""
            targets.any { target ->
                val cleanTarget = target.replace(".0", "").trim()
                val regex = "(?:^|\\D)${Regex.escape(cleanTarget)}(?:$|\\D)".toRegex()
                regex.containsMatchIn(rawLine)
            }
        }.mapNotNull { bus ->
            val lat = bus.latitude?.replace(",", ".")?.toDoubleOrNull()
            val lng = bus.longitude?.replace(",", ".")?.toDoubleOrNull()
            val speed = bus.velocidade?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
            
            if (lat != null && lng != null) {
                BusInfo(
                    id = bus.ordem ?: "Unknown",
                    linha = bus.linha?.replace(".0", "") ?: "Unknown",
                    lat = lat,
                    lng = lng,
                    velocidade = speed,
                    tipo = BusTypeInfo.MUNICIPAL
                )
            } else null
        }
    }
    
    // ============================================
    // DISK CACHE PERSISTENCE (Instant first load)
    // ============================================
    
    private data class CacheEntry(
        val data: List<BusInfo>,
        val timestamp: Long,
        val lines: Set<String>
    )
    
    /**
     * Save bus data to disk for instant loading on app restart
     */
    private fun saveToDiskCache(fileName: String, data: List<BusInfo>, lines: Set<String>) {
        try {
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val cacheFile = File(cacheDir, fileName)
            val entry = CacheEntry(data, System.currentTimeMillis(), lines)
            cacheFile.writeText(gson.toJson(entry))
            Log.d("BusRepository", "Saved ${data.size} buses to disk cache: $fileName")
        } catch (e: Exception) {
            Log.e("BusRepository", "Failed to save disk cache: ${e.message}")
        }
    }
    
    /**
     * Load bus data from disk cache (called on app start)
     * Returns null if cache doesn't exist or is too old
     */
    private fun loadFromDiskCache(fileName: String, requestedLines: Set<String>): List<BusInfo>? {
        return try {
            val cacheFile = File(cacheDir, fileName)
            if (!cacheFile.exists()) return null
            
            val json = cacheFile.readText()
            val type = object : TypeToken<CacheEntry>() {}.type
            val entry: CacheEntry = gson.fromJson(json, type)
            
            val age = System.currentTimeMillis() - entry.timestamp
            
            // Return data if within stale threshold AND lines match
            if (age < STALE_CACHE_DURATION_MS && entry.lines == requestedLines) {
                Log.d("BusRepository", "Loaded ${entry.data.size} buses from disk cache (age: ${age/1000}s)")
                entry.data
            } else {
                Log.d("BusRepository", "Disk cache expired or lines changed (age: ${age/1000}s)")
                null
            }
        } catch (e: Exception) {
            Log.e("BusRepository", "Failed to load disk cache: ${e.message}")
            null
        }
    }
    
    /**
     * Initialize caches from disk on first access (called from fetchBuses)
     */
    private var diskCacheLoaded = false
    private suspend fun loadDiskCacheIfNeeded(rioLines: Set<String>, detroLines: Set<String>) {
        if (diskCacheLoaded) return
        diskCacheLoaded = true
        
        withContext(Dispatchers.IO) {
            // Try to populate memory cache from disk
            if (rioCacheData.isEmpty()) {
                loadFromDiskCache(RIO_CACHE_FILE, rioLines)?.let { data ->
                    rioCacheData = data
                    rioCacheTime = System.currentTimeMillis() - CACHE_DURATION_MS + 5000 // Treat as stale but valid
                    rioCacheLines = rioLines
                }
            }
            if (detroCacheData.isEmpty()) {
                loadFromDiskCache(DETRO_CACHE_FILE, detroLines)?.let { data ->
                    detroCacheData = data
                    detroCacheTime = System.currentTimeMillis() - CACHE_DURATION_MS + 5000
                    detroCacheLines = detroLines
                }
            }
        }
    }
    
    /**
     * Get official route shape coordinates for a line (from bundled GTFS data)
     * Returns list of [lat, lng] pairs or null if line not found
     */
    suspend fun getRouteShape(lineName: String): List<List<Double>>? = withContext(Dispatchers.IO) {
        // Load route shapes if not loaded yet
        if (routeShapes == null) {
            try {
                Log.d("BusRepository", "Loading route_shapes.json from assets...")
                val json = context.assets.open("route_shapes.json").bufferedReader().use { it.readText() }
                val type = object : com.google.gson.reflect.TypeToken<Map<String, List<List<Double>>>>() {}.type
                routeShapes = gson.fromJson(json, type)
                Log.d("BusRepository", "Route shapes loaded! ${routeShapes?.size} lines")
            } catch (e: Exception) {
                Log.e("BusRepository", "Error loading route shapes", e)
                routeShapes = emptyMap()
            }
        }
        
        // Try exact match first, then try variations
        return@withContext routeShapes?.get(lineName) 
            ?: routeShapes?.get(lineName.uppercase())
            ?: routeShapes?.get(lineName.replace(".0", ""))
    }

    suspend fun fetchBuses(activeLines: List<String>): List<BusInfo> = withContext(Dispatchers.IO) {
        if (database == null) loadDatabase()
        val db = database ?: return@withContext emptyList()

        if (activeLines.isEmpty()) {
            Log.d("BusRepository", "No active lines to fetch.")
            return@withContext emptyList()
        }

        Log.d("BusRepository", "Fetching buses for lines: $activeLines")

        val detroTargets = mutableListOf<String>()
        val rioTargets = mutableListOf<String>()

        // PRIORITY: Check Rio first (Worker is faster and more reliable)
        // If line exists in Rio, use Rio. Only use Detro if line is ONLY in Detro.
        activeLines.forEach { line ->
            if (db.rio.containsKey(line)) {
                rioTargets.add(line)
            } else if (db.detro.containsKey(line)) {
                detroTargets.add(line)
            } else {
                // Default to Rio for unknown lines (Worker will filter)
                rioTargets.add(line)
            }
        }
        
        DebugLogger.log("Fetch", "Rio: $rioTargets | Detro: $detroTargets")
        
        // Load disk cache on first access (instant first load!)
        loadDiskCacheIfNeeded(rioTargets.toSet(), detroTargets.toSet())

        val detroDeferred = mutableListOf<Deferred<List<BusInfo>>>()
        var cachedDetroResults: List<BusInfo> = emptyList()
        
        // DETRO FETCH with CACHING
        if (detroTargets.isNotEmpty()) {
            val currentTime = System.currentTimeMillis()
            val detroCacheValid = detroCacheTime > 0 && 
                                  (currentTime - detroCacheTime) < CACHE_DURATION_MS &&
                                  detroTargets.toSet() == detroCacheLines
            
            // If cache is valid, use cached data (instant response!)
            if (detroCacheValid && detroCacheData.isNotEmpty()) {
                Log.d("BusRepository", "Using CACHED Detro data (${detroCacheData.size} buses)")
                cachedDetroResults = detroCacheData
            } else {
                Log.d("BusRepository", "Fetching fresh Detro data...")
                // Fetch from API
                for (line in detroTargets) {
                    val config = db.detro[line] ?: continue
                    val jobs = listOf(1, 2).map { sentido ->
                        async {
                            try {
                                val response = detroApi.getDetroBuses(
                                    guid = config.guid,
                                    idEmpresa = config.idEmpresa,
                                    linha = config.linha,
                                    sentido = sentido
                                )
                                if (response.isSuccessful) {
                                    response.body()?.mapNotNull { raw ->
                                        val lat = raw.gps?.latitude
                                        val lng = raw.gps?.longitude
                                        if (lat != null && lng != null) {
                                            BusInfo(
                                                id = raw.idVeiculo ?: "Unknown",
                                                linha = line,
                                                lat = lat,
                                                lng = lng,
                                                velocidade = raw.gps.velocidade ?: 0.0,
                                                tipo = BusTypeInfo.INTERMUNICIPAL,
                                                sentido = if (sentido == 1) "IDA" else "VOLTA"
                                            )
                                        } else null
                                    } ?: emptyList()
                                } else {
                                    Log.e("BusRepository", "Detro Error ${response.code()} for $line")
                                    emptyList()
                                }
                            } catch (e: Exception) {
                                Log.e("BusRepository", "Detro Exception for $line: ${e.message}")
                                emptyList()
                            }
                        }
                    }
                    detroDeferred.addAll(jobs)
                }
            }
        }

        // RIO FETCH - Use GLOBAL CACHE for instant response!
        val rioDeferred = mutableListOf<Deferred<List<BusInfo>>>()
        
        if (rioTargets.isNotEmpty()) {
             val currentTime = System.currentTimeMillis()
             
             // PRIORITY 1: Use global cache if available (INSTANT! <100ms)
             val globalCacheValid = globalRioCache.isNotEmpty() && 
                                    (currentTime - globalRioCacheTime) < STALE_CACHE_DURATION_MS
             
             if (globalCacheValid) {
                 Log.d("BusRepository", "Using GLOBAL CACHE for instant Rio search (${globalRioCache.size} total buses)")
                 val filteredBuses = filterRioBusesFromGlobalCache(rioTargets)
                 Log.d("BusRepository", "Filtered to ${filteredBuses.size} buses for requested lines")
                 
                 // Combine with Detro and return immediately
                 val detroResults = if (cachedDetroResults.isNotEmpty()) {
                     cachedDetroResults
                 } else {
                     detroDeferred.awaitAll().flatten()
                 }
                 
                 // Trigger background refresh if cache is getting stale (>30s)
                 if ((currentTime - globalRioCacheTime) > CACHE_DURATION_MS) {
                     Log.d("BusRepository", "Cache stale, triggering background refresh...")
                     backgroundScope.launch {
                         prefetchAllRioBuses()
                     }
                 }
                 
                 return@withContext processOverlaps(detroResults + filteredBuses)
             }
             
             // PRIORITY 2: Use line-specific cache
             val cacheValid = rioCacheTime > 0 && 
                              (currentTime - rioCacheTime) < CACHE_DURATION_MS &&
                              rioTargets.toSet() == rioCacheLines
             
              // If cache is valid, return cached data immediately (instant response!)
             if (cacheValid && rioCacheData.isNotEmpty()) {
                 Log.d("BusRepository", "Using CACHED Rio data (${rioCacheData.size} buses)")
                 DebugLogger.log("Cache", "HIT: ${rioCacheData.size} total, filtering for $rioTargets")
                 // Still need to combine with detro results below
                 val detroResults = detroDeferred.awaitAll().flatten()
                 val cachedRioFiltered = rioCacheData.filter { bus -> 
                     rioTargets.any { target ->
                         val cleanTarget = target.replace(".0", "").trim()
                         bus.linha.contains(cleanTarget)
                     }
                 }
                 DebugLogger.log("Cache", "Filtered: ${cachedRioFiltered.size} buses for Rio")
                 return@withContext processOverlaps(detroResults + cachedRioFiltered)
             }
             
             Log.d("BusRepository", "Cache miss or expired, fetching fresh Rio data...")
             DebugLogger.log("API", "Fetching fresh data for $rioTargets")
             
             // ALWAYS use single combined request (reduces 503 errors)
             val linesParam = rioTargets.joinToString(",")
             val job = async {
                var attempts = 0
                val maxAttempts = 3
                var lastResult: List<BusInfo> = emptyList()
                
                while (attempts < maxAttempts) {
                    attempts++
                    try {
                        Log.d("BusRepository", "Rio API attempt $attempts for: $linesParam")
                        val response = rioApi.getRioBuses(linha = linesParam)
                        if (response.isSuccessful) {
                            val allBuses = response.body() ?: emptyList()
                            Log.d("BusRepository", "Rio API returned ${allBuses.size} buses")
                            DebugLogger.log("Rio", "$linesParam: ${allBuses.size} buses")
                            // Worker already filters, just map to BusInfo
                            lastResult = allBuses.mapNotNull { bus ->
                                 val lat = bus.latitude?.replace(",", ".")?.toDoubleOrNull()
                                 val lng = bus.longitude?.replace(",", ".")?.toDoubleOrNull()
                                 val speed = bus.velocidade?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                                 
                                 if (lat != null && lng != null) {
                                     BusInfo(
                                         id = bus.ordem ?: "Unknown",
                                         linha = bus.linha?.replace(".0", "") ?: "Unknown",
                                         lat = lat,
                                         lng = lng,
                                         velocidade = speed,
                                         tipo = BusTypeInfo.MUNICIPAL
                                     )
                                 } else null
                            }
                            DebugLogger.log("Parse", "Parsed: ${lastResult.size}/${allBuses.size} buses")
                            break // Success!
                        } else {
                            Log.e("BusRepository", "Rio API Error ${response.code()} (attempt $attempts)")
                            DebugLogger.log("Rio", "ERROR ${response.code()} (attempt $attempts)")
                            if (attempts < maxAttempts && (response.code() == 502 || response.code() == 503)) {
                                kotlinx.coroutines.delay(attempts * 1000L) // 1s, 2s, 3s
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("BusRepository", "Rio API Exception (attempt $attempts): ${e.message}")
                        DebugLogger.log("Rio", "EXCEPTION (attempt $attempts): ${e.message}")
                        if (attempts < maxAttempts) {
                            kotlinx.coroutines.delay(attempts * 1000L)
                        }
                    }
                }
                lastResult
             }
             rioDeferred.add(job)
         }

        // Combine results - use cached data if available, otherwise use fresh fetch
        val detroResults = if (cachedDetroResults.isNotEmpty()) {
            cachedDetroResults
        } else {
            val freshDetro = detroDeferred.awaitAll().flatten()
            // Update cache for Detro data
            if (freshDetro.isNotEmpty()) {
                detroCacheData = freshDetro
                detroCacheTime = System.currentTimeMillis()
                detroCacheLines = detroTargets.toSet()
                Log.d("BusRepository", "Updated Detro cache with ${freshDetro.size} buses")
                // Persist to disk for instant load on next app start
                saveToDiskCache(DETRO_CACHE_FILE, freshDetro, detroCacheLines)
            }
            freshDetro
        }
        
        val rioResults = rioDeferred.awaitAll().flatten()
        
        // Update cache for Rio data
        if (rioResults.isNotEmpty()) {
            rioCacheData = rioResults
            rioCacheTime = System.currentTimeMillis()
            rioCacheLines = rioTargets.toSet()
            Log.d("BusRepository", "Updated Rio cache with ${rioResults.size} buses")
            // Persist to disk for instant load on next app start
            saveToDiskCache(RIO_CACHE_FILE, rioResults, rioCacheLines)
        }

        Log.d("BusRepository", "Total Buses Found: ${detroResults.size + rioResults.size} (Detro: ${detroResults.size}, Rio: ${rioResults.size})")
        
        return@withContext processOverlaps(detroResults + rioResults)
    }
    
    /**
     * Process overlapping bus positions by offsetting them slightly
     * so they don't stack on top of each other on the map.
     */
    private fun processOverlaps(allBuses: List<BusInfo>): List<BusInfo> {
        val processedBuses = mutableListOf<BusInfo>()
        val locationMap = mutableMapOf<String, Int>()
        
        allBuses.forEach { bus ->
            val key = "${bus.lat},${bus.lng}"
            val count = locationMap.getOrDefault(key, 0)
            locationMap[key] = count + 1
            
            if (count > 0) {
                // Offset ~10m per overlap
                val offset = 0.0001 * count
                // Alternate directions
                val latOffset = if (count % 2 == 0) offset else -offset
                val lngOffset = if (count % 4 < 2) offset else -offset
                
                processedBuses.add(bus.copy(
                    lat = bus.lat + latOffset,
                    lng = bus.lng + lngOffset
                ))
            } else {
                processedBuses.add(bus)
            }
        }

        Log.d("BusRepository", "Total Buses Processed: ${processedBuses.size}")
        return processedBuses
    }
}
