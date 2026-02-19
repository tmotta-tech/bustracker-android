package com.example.bustracker.repository

import android.content.Context
import com.example.bustracker.api.BusService
import com.example.bustracker.data.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class BusRepository(private val context: Context) {

    private val gson = Gson()
    private var database: BusDatabase? = null

    // -- Retrofit Clients --
    // DETRO base URL
    private val detroApi = Retrofit.Builder()
        .baseUrl("https://appbus.exall-host.com.br/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(BusService::class.java)

    // RIO base URL
    private val rioApi = Retrofit.Builder()
        .baseUrl("https://dados.mobilidade.rio/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(BusService::class.java)

    // Load Database from Assets (bus_database.json)
    suspend fun loadDatabase() {
        if (database != null) return
        withContext(Dispatchers.IO) {
            try {
                val json = context.assets.open("bus_database.json").bufferedReader().use { it.readText() }
                database = gson.fromJson(json, BusDatabase::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Main Function to Fetch Buses
    suspend fun fetchBuses(activeLines: List<String>): List<Bus> = withContext(Dispatchers.IO) {
        if (database == null) loadDatabase()
        val db = database ?: return@withContext emptyList()

        if (activeLines.isEmpty()) return@withContext emptyList()

        val detroTargets = mutableListOf<String>()
        val rioTargets = mutableListOf<String>()

        // 1. Separate Lines (Detro vs Rio)
        activeLines.forEach { line ->
            if (db.detro.containsKey(line)) {
                detroTargets.add(line)
            } else {
                rioTargets.add(line)
            }
        }

        // 2. Fetch DETRO (Parallel Requests per line)
        val detroDeferred = detroTargets.flatMap { line ->
            val config = db.detro[line] ?: return@flatMap emptyList()
            // We need 2 requests per line (IDA and VOLTA)
            listOf(1, 2).map { sentido ->
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
                                    Bus(
                                        id = raw.idVeiculo ?: "Unknown",
                                        linha = line,
                                        lat = lat,
                                        lng = lng,
                                        velocidade = raw.gps.velocidade ?: 0.0,
                                        tipo = BusType.INTERMUNICIPAL,
                                        sentido = if (sentido == 1) "IDA" else "VOLTA"
                                    )
                                } else null
                            } ?: emptyList()
                        } else emptyList()
                    } catch (e: Exception) {
                        emptyList<Bus>()
                    }
                }
            }
        }

        // 3. Fetch RIO (Single Global Request)
        val rioDeferred = if (rioTargets.isNotEmpty()) {
            async {
                try {
                    val response = rioApi.getRioBuses()
                    if (response.isSuccessful) {
                        response.body()?.filter { bus ->
                            // Fuzzy Matching Logic
                            val rawLine = bus.linha ?: ""
                            rioTargets.any { target ->
                                rawLine == target || 
                                rawLine.replace(".0", "") == target ||
                                rawLine.contains(target)
                            }
                        }?.mapNotNull { bus ->
                             if (bus.latitude != null && bus.longitude != null) {
                                 Bus(
                                     id = bus.ordem ?: "Unknown",
                                     linha = bus.linha?.replace(".0", "") ?: "Unknown",
                                     lat = bus.latitude,
                                     lng = bus.longitude,
                                     velocidade = bus.velocidade ?: 0.0,
                                     tipo = BusType.MUNICIPAL
                                 )
                             } else null
                        } ?: emptyList()
                    } else emptyList()
                } catch (e: Exception) {
                    emptyList<Bus>()
                }
            }
        } else null

        // 4. Merge Results
        val detroResults = detroDeferred.awaitAll().flatten()
        val rioResults = rioDeferred?.await() ?: emptyList()

        return@withContext detroResults + rioResults
    }
}
