package com.example.bustrackernativo

import android.util.Log
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.animation.LinearInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.example.bustrackernativo.data.BusInfo
import com.example.bustrackernativo.data.getFreshnessAlpha
import com.example.bustrackernativo.data.DebugLogger
import com.example.bustrackernativo.data.SettingsDataStore
import com.example.bustrackernativo.ui.map.ActiveLineChips
import com.example.bustrackernativo.ui.map.BusDetailCard
import com.example.bustrackernativo.ui.map.BusViewModel
import com.example.bustrackernativo.ui.map.SearchBar
import com.example.bustrackernativo.ui.theme.BusTrackerNativoTheme
import com.example.bustrackernativo.ui.util.lineToColor
import kotlinx.coroutines.launch
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.maps.android.SphericalUtil

class MainActivity : ComponentActivity() {
    private val mapViewModel: BusViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configuration.getInstance() removed (OsmDroid specific)

        setContent {
            BusTrackerNativoTheme {
                BusTrackerApp(viewModel = mapViewModel)
            }
        }
    }
    
    override fun onStart() {
        super.onStart()
        // Resume updates when app becomes visible
        mapViewModel.resumeTracking()
    }
    
    override fun onStop() {
        super.onStop()
        // Pause updates when app goes to background - saves battery and data
        mapViewModel.pauseTracking()
    }
}

@Composable
fun BusTrackerApp(viewModel: BusViewModel) {
    val buses by viewModel.buses.observeAsState(emptyList())
    val activeLines by viewModel.activeLines.observeAsState(emptyList())
    val suggestions by viewModel.suggestions.observeAsState(emptyList())
    val routeShapes by viewModel.routeShapes.observeAsState(emptyMap())
    val isLoading by viewModel.loading.observeAsState(false) // Monitor loading state
    val isDarkTheme = isSystemInDarkTheme()
    val context = LocalContext.current
    
    // Settings
    val settings = remember { SettingsDataStore.getInstance(context) }
    var lowPerformanceMode by remember { mutableStateOf(settings.lowPerformanceMode) }
    var debugMode by remember { mutableStateOf(settings.debugMode) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var debugLogs by remember { mutableStateOf(DebugLogger.getLogs()) }
    
    // Update debug logs when they change
    LaunchedEffect(debugMode) {
        if (debugMode) {
            DebugLogger.onLogAdded = { debugLogs = DebugLogger.getLogs() }
        }
    }
    
    // State to trigger location center
    val centerUser = remember { mutableStateOf(false) }
    
    // State for Selected Bus (Bottom Sheet / Card)
    var selectedBus by remember { mutableStateOf<BusInfo?>(null) }
    
    // Clear selection when searching or touching map
    LaunchedEffect(activeLines) { selectedBus = null }
    
    // Settings Dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Configurações") },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Modo Economia", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Para celulares fracos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = lowPerformanceMode,
                            onCheckedChange = { 
                                lowPerformanceMode = it
                                settings.lowPerformanceMode = it
                            }
                        )
                    }
                    if (lowPerformanceMode) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "• Máx 30 ônibus\n• Sem animações\n• Sem linhas de rota",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Debug Mode Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Modo Debug", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Mostra logs de API",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = debugMode,
                            onCheckedChange = { 
                                debugMode = it
                                settings.debugMode = it
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Icon Style Selector
                    var iconStyle by remember { mutableStateOf(settings.iconStyle) }
                    Column {
                        Text("Estilo do Ícone", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Gota option
                            FilterChip(
                                selected = iconStyle == "drop",
                                onClick = { 
                                    iconStyle = "drop"
                                    settings.iconStyle = "drop"
                                },
                                label = { Text("💧 Gota") }
                            )
                            // Ônibus option
                            FilterChip(
                                selected = iconStyle == "bus",
                                onClick = { 
                                    iconStyle = "bus"
                                    settings.iconStyle = "bus"
                                },
                                label = { Text("🚌 Ônibus") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            BusTrackerMapScreen(
                viewModel = viewModel,
                buses = buses, 
                routeShapes = routeShapes,
                centerUser = centerUser.value,
                onUserCentered = { centerUser.value = false },
                isDarkTheme = isDarkTheme,
                lowPerformanceMode = lowPerformanceMode,
                selectedBus = selectedBus,
                onBusSelected = { bus -> selectedBus = bus },
                onMapClick = { selectedBus = null }
            )

            // Bus Detail Card (Animated Bottom Sheet)
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                 BusDetailCard(
                    bus = selectedBus,
                    onClose = { selectedBus = null },
                    modifier = Modifier.padding(bottom = 16.dp) // Lift slightly
                )
            }

            // Search Bar Container
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SearchBar(
                    onAddLine = viewModel::addLine,
                    onSearch = viewModel::search,
                    suggestions = suggestions,
                    onClearSuggestions = viewModel::clearSuggestions
                )
            }

            // Loading Indicators
            if (isLoading) {
                // 1. Top Linear Progress
                androidx.compose.material3.LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = androidx.compose.ui.graphics.Color.Transparent
                )

                // 2. Floating Badge "Atualizando..."
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 85.dp), // Below SearchBar
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f),
                    tonalElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Atualizando...",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            
            // Active Lines at Bottom Start (Left)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 80.dp)
            ) {
                 ActiveLineChips(
                    lines = activeLines,
                    onRemoveLine = viewModel::removeLine
                )
            }
            
            // Settings Gear Icon (Bottom Left, above active lines)
            FloatingActionButton(
                onClick = { showSettingsDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 140.dp)
                    .size(40.dp),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                elevation = FloatingActionButtonDefaults.elevation(2.dp)
            ) {
                Icon(
                    Icons.Default.Settings, 
                    contentDescription = "Configurações",
                    modifier = Modifier.size(20.dp)
                )
            }
            
            // Floating Action Button for Location
            FloatingActionButton(
                onClick = { centerUser.value = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Minha Localização")
            }
            
            // Debug Log Panel (when debug mode is enabled)
            if (debugMode) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 80.dp, start = 8.dp, end = 8.dp)
                        .fillMaxWidth()
                        .height(150.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 4.dp
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            "📋 Debug Logs",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            Column {
                                debugLogs.takeLast(8).forEach { log ->
                                    Text(
                                        log,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}



@Composable
fun BusTrackerMapScreen(
    viewModel: BusViewModel,
    buses: List<BusInfo>,
    routeShapes: Map<String, List<List<Double>>>,
    centerUser: Boolean,
    onUserCentered: () -> Unit,
    isDarkTheme: Boolean,
    lowPerformanceMode: Boolean = false,
    selectedBus: BusInfo? = null,
    onBusSelected: (BusInfo) -> Unit,
    onMapClick: () -> Unit
) {
    val context = LocalContext.current
    
    // Vibrator for proximity alerts
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
    
    // Track if we already vibrated for this approach
    var hasVibratedForApproach by remember { mutableStateOf(false) }
    
    // Permission State
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Permission Launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        }
    )

    // Request permissions on start if not granted
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }
    
    // Initial Camera Position (Rio de Janeiro)
    val rio = LatLng(-22.9068, -43.1729)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(rio, 12.5f)
    }

    // Effect to Center User
    LaunchedEffect(centerUser) {
        if (centerUser) {
             if (hasLocationPermission) {
                 // My Location button handles this natively, but if triggered programmatically:
                 // Ideally we'd need FusedLocationProvider here to animate manually.
                 // For now, checking permission prevents crash.
                 onUserCentered()
             } else {
                 // Trigger permission request again if user clicks and missing
                 locationPermissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                 )
                 onUserCentered() // Reset flag
             }
        }
    }

    // Map Properties (Style, Dark Mode, My Location)
    val mapProperties = remember(isDarkTheme, hasLocationPermission) {
        MapProperties(
            isMyLocationEnabled = hasLocationPermission, 
            mapStyleOptions = if (isDarkTheme) {
                 MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark_gold)
            } else {
                 MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_light_gold)
            },
            isBuildingEnabled = !lowPerformanceMode,
            isIndoorEnabled = false,
            isTrafficEnabled = false,
            minZoomPreference = 10f
        )
    }

    val mapUiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false, 
            myLocationButtonEnabled = false, // We use our custom FAB
            mapToolbarEnabled = false
        )
    }
    
    // Current Zoom for Simplification Logic
    val currentZoom = cameraPositionState.position.zoom
    
    // ========== PROXIMITY ALERT ==========
    // Vibrate when selected bus approaches user location (within 200m)
    LaunchedEffect(selectedBus, cameraPositionState.position.target) {
        if (selectedBus != null && hasLocationPermission) {
            // Use camera position as approximate user location (when centered on user)
            // Note: For more precise location, use FusedLocationProviderClient
            val userApproxLocation = cameraPositionState.position.target
            val busLocation = LatLng(selectedBus.lat, selectedBus.lng)
            val distance = SphericalUtil.computeDistanceBetween(userApproxLocation, busLocation)
            
            if (distance < 200 && !hasVibratedForApproach) {
                // Vibrate to alert user
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(500)
                }
                hasVibratedForApproach = true
            } else if (distance >= 300) {
                // Reset vibration flag when bus is far again
                hasVibratedForApproach = false
            }
        }
    }
    
    // Reset vibration flag when selecting a different bus
    LaunchedEffect(selectedBus?.id) {
        hasVibratedForApproach = false
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = mapProperties,
        uiSettings = mapUiSettings,
        onMapClick = { 
            onMapClick()    
        }
    ) {
        // ========== OFFICIAL ROUTE POLYLINES ==========
        if (!lowPerformanceMode && currentZoom >= 13f) {
             routeShapes.forEach { (lineName, coordinates) ->
                if (coordinates.size >= 2) {
                    val points = coordinates.map { LatLng(it[0], it[1]) }
                    val lineColor = lineToColor(lineName)
                    
                    Polyline(
                        points = points,
                        color = lineColor,
                        width = 12f, // width in pixels approx
                        geodesic = true,
                        zIndex = 0f // Below markers
                    )
                }
            }
        }

        // ========== BUS MARKERS ==========
        // Simplification: Hide if zoom < 11.5
        val displayBuses = remember(currentZoom, lowPerformanceMode, buses) {
             if (currentZoom < 11.5f && !lowPerformanceMode) {
                emptyList()
            } else if (lowPerformanceMode) {
                 buses.take(30)
            } else {
                 buses.take(100) // Render up to 100 markers
            }
        }

        displayBuses.forEach { bus ->
            // Use key to prevent unnecessary recompositions of the marker node
            androidx.compose.runtime.key(bus.id) {
                val targetPosition = LatLng(bus.lat, bus.lng)
                
                // State for the marker position mechanism
                val markerState = remember { MarkerState(position = targetPosition) }
                
                // Animation Logic
                if (!lowPerformanceMode) {
                    LaunchedEffect(targetPosition) {
                        try {
                            val startPosition = markerState.position
                            // Animating from 0f to 1f
                            androidx.compose.animation.core.animate(
                                initialValue = 0f,
                                targetValue = 1f,
                                animationSpec = androidx.compose.animation.core.tween(
                                    durationMillis = 1000, 
                                    easing = androidx.compose.animation.core.LinearEasing
                                )
                            ) { fraction, _ ->
                                markerState.position = SphericalUtil.interpolate(startPosition, targetPosition, fraction.toDouble())
                            }
                        } catch (e: Exception) {
                            markerState.position = targetPosition // Fallback snap
                        }
                    }
                } else {
                     // In low performance mode, just snap
                     markerState.position = targetPosition
                }
                
                // Calculate rotation based on previous position (bearing)
                val rotation = viewModel.calculateBearing(bus)
                
                // Calculate freshness alpha based on data age
                val freshnessAlpha = bus.getFreshnessAlpha()
                
                // Use Cached Icon
                val iconDescriptor = remember(bus.linha, freshnessAlpha, isDarkTheme, context) {
                     BitmapDescriptorCache.get(context, bus.linha, freshnessAlpha, 
                        isDarkTheme
                     )
                }

                Marker(
                    state = markerState,
                    title = "Linha ${bus.linha}",
                    snippet = "Velocidade: ${bus.velocidade} km/h",
                    icon = iconDescriptor,
                    rotation = rotation,
                    anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.85f),
                    zIndex = 1f,
                    onClick = { 
                        onBusSelected(bus)
                        true
                    }
                )
            }
        }
    }
}

// Singleton Cache Object
object BitmapDescriptorCache {
    private val cache = androidx.collection.LruCache<String, BitmapDescriptor>(100) // Keep last 100 icons

    fun get(context: android.content.Context, line: String, alpha: Float, isDark: Boolean): BitmapDescriptor? {
        // Read current style from settings (this is fast as it's a synchronous Prefs lookup or we should pass it)
        // For performance, we'll assume the style doesn't change every frame. 
        // Ideally pass style as param.
        val settings = SettingsDataStore.getInstance(context)
        val style = settings.iconStyle
        
        val key = "${line}_${alpha}_${style}_$isDark"
        
        return cache[key] ?: create(context, line, alpha, style).also { 
            if (it != null) cache.put(key, it) 
        }
    }

    private fun create(context: android.content.Context, line: String, alpha: Float, iconStyle: String): BitmapDescriptor? {
        val busColorInt = lineToColor(line).toArgb()
        
        val density = context.resources.displayMetrics.density
        val widthPx = (22 * density).toInt()
        val heightPx = (28 * density).toInt()
        val sizePx = (22 * density).toInt()
        
        val alphaInt = (alpha * 255).toInt().coerceIn(0, 255)

        return try {
            if (iconStyle == "drop") {
                 val base = ContextCompat.getDrawable(context, R.drawable.ic_modern_base)?.mutate()
                 val fill = ContextCompat.getDrawable(context, R.drawable.ic_modern_fill)?.mutate()
                 val hole = ContextCompat.getDrawable(context, R.drawable.ic_modern_hole)?.mutate()
                 
                 if (base != null && fill != null && hole != null) {
                     val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                     val canvas = Canvas(bitmap)
                     
                     val paint = Paint().apply { this.alpha = alphaInt }
                     
                     base.setBounds(0, 0, widthPx, heightPx)
                     base.alpha = alphaInt
                     base.draw(canvas)
                     
                     DrawableCompat.setTint(fill, busColorInt)
                     fill.setBounds(0, 0, widthPx, heightPx)
                     fill.alpha = alphaInt
                     fill.draw(canvas)
                     
                     hole.setBounds(0, 0, widthPx, heightPx)
                     hole.alpha = alphaInt
                     hole.draw(canvas)
                     
                     BitmapDescriptorFactory.fromBitmap(bitmap)
                 } else null
            } else {
                 val bg = ContextCompat.getDrawable(context, R.drawable.ic_marker_circle_bg)?.mutate()
                 val icon = ContextCompat.getDrawable(context, R.drawable.ic_icon_bus_white)?.mutate()
                 
                 if (bg != null && icon != null) {
                      val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                      val canvas = Canvas(bitmap)
                      
                      DrawableCompat.setTint(bg, busColorInt)
                      bg.setBounds(0, 0, sizePx, sizePx)
                      bg.alpha = alphaInt
                      bg.draw(canvas)
                      
                      val pad = (sizePx * 0.2f).toInt()
                      icon.setBounds(pad, pad, sizePx - pad, sizePx - pad)
                      icon.alpha = alphaInt
                      icon.draw(canvas)
                      
                      BitmapDescriptorFactory.fromBitmap(bitmap)
                 } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            BitmapDescriptorFactory.defaultMarker()
        }
    }
}
