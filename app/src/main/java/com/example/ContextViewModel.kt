package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import android.app.PendingIntent
import android.content.BroadcastReceiver

data class AtmosphereContext(
    val battery: Int = 100,
    val isOffline: Boolean = false,
    val networkName: String = "WIFI",
    val hasFocusEvent: Boolean = false,
    val speedMph: Float = 0f,
    val address: String = "Unknown",
    val weather: String = "Clear",
    val latPattern: String = "LAT: --",
    val isSocialMode: Boolean = false,
    val timeHour: Int = 12,
    val ambientNoiseLevel: Double = 0.0,
    val sustainedLoudNoise: Boolean = false,
    val detectedActivity: Int = 4, // 4 is UNKNOWN
    val isInternetCafe: Boolean = false,
    val isHeadphonesConnected: Boolean = false,
    val isWeekend: Boolean = false
)

fun evaluateState(ctx: AtmosphereContext): WidgetState {
    if (ctx.isOffline || ctx.battery < 15) return WidgetState.GHOST
    
    // Deep Focus: Focus event (calendar) + high ambient noise (e.g. busy cafe)
    if (ctx.hasFocusEvent && ctx.ambientNoiseLevel >= 65.0) return WidgetState.DEEP_FOCUS

    if (ctx.address.contains("Party", true) || ctx.isSocialMode || ctx.sustainedLoudNoise) return WidgetState.CROWD
    
    // Gym + Headphones -> BEAST, Gym without Headphones -> DISCOVERY (handled by fallback if no other matched)
    if ((ctx.address.contains("Gym", true) && ctx.isHeadphonesConnected) || ctx.detectedActivity == 8 /* RUNNING */ || ctx.detectedActivity == 1 /* ON_BICYCLE */) return WidgetState.BEAST
    
    if ((ctx.timeHour >= 23 || ctx.timeHour < 5) && ctx.isHeadphonesConnected) return WidgetState.BEDTIME

    if ((ctx.timeHour >= 18 && (ctx.battery < 50 || (ctx.ambientNoiseLevel > 0.0 && ctx.ambientNoiseLevel <= 50.0))) || (ctx.address.contains("Home", true) && (ctx.timeHour >= 18 || ctx.isWeekend))) return WidgetState.REWIND
    if (ctx.hasFocusEvent || ctx.isInternetCafe || (ctx.ambientNoiseLevel > 0.0 && ctx.ambientNoiseLevel <= 50.0)) return WidgetState.FOCUS
    if (ctx.weather == "Rain" || ctx.weather == "Storm" || ctx.weather == "Snow" || ctx.weather == "Drizzle" || ctx.weather == "Fog" || ctx.weather == "Cloudy" || ctx.timeHour < 6) return WidgetState.COZY
    return WidgetState.DISCOVERY
}

class ContextViewModel(application: Application) : AndroidViewModel(application) {
    private val ctx = application.applicationContext

    private val _atmosphereContext = MutableStateFlow(AtmosphereContext())
    val atmosphereContext: StateFlow<AtmosphereContext> = _atmosphereContext.asStateFlow()

    private val _currentState = MutableStateFlow(WidgetState.DISCOVERY)
    val currentState: StateFlow<WidgetState> = _currentState.asStateFlow()

    private val _isManualOverride = MutableStateFlow(false)
    private var overrideEndTime: Long = 0

    private val noiseHistory = mutableListOf<Double>()
    private var currentActivityType = 4 // 4 is UNKNOWN

    private val syncMutex = kotlinx.coroutines.sync.Mutex()
    
    private var isAppInForeground = false

    fun setAppForeground(isForeground: Boolean) {
        isAppInForeground = isForeground
    }

    private var sensorManager: android.hardware.SensorManager? = null
    private var stepDetector: android.hardware.Sensor? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                syncContext()
                val currentBattery = _atmosphereContext.value.battery
                val pollDelay = if (currentBattery < 15) 60_000L else 10_000L
                delay(pollDelay)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            com.example.ActivityGlobalState.detectedActivity.collect {
                syncContext()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun onPermissionsGranted() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && 
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACTIVITY_RECOGNITION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return
        }
        
        try {
            sensorManager = ctx.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
            stepDetector = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_STEP_DETECTOR)
            stepDetector?.let {
                sensorManager?.registerListener(object : android.hardware.SensorEventListener {
                    override fun onSensorChanged(event: android.hardware.SensorEvent?) {
                        if (event?.values?.get(0) == 1.0f) { // A step was detected
                            com.example.ActivityGlobalState.detectedActivity.value = 7 // WALKING
                        }
                    }
                    override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
                }, it, android.hardware.SensorManager.SENSOR_DELAY_FASTEST)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isHeadphonesConnected(): Boolean {
        return try {
            val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val devices = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
            devices.any { 
                it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || 
                it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES || 
                it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun checkInternetCafe(ssid: String, noise: Double): Boolean {
        // If connected to common cafe WiFis or contains Cafe/Coffee
        val lowercaseSsid = ssid.lowercase()
        return (lowercaseSsid.contains("starbucks") || lowercaseSsid.contains("cafe") || lowercaseSsid.contains("coffee") || lowercaseSsid.contains("guest")) && noise > 45.0
    }

    fun overrideState() {
        val states = WidgetState.values()
        val nextIdx = (states.indexOf(_currentState.value) + 1) % states.size
        _currentState.value = states[nextIdx]
        _isManualOverride.value = true
        overrideEndTime = System.currentTimeMillis() + 1000 * 60 * 60 // 1 hour duration
    }
    
    fun onAppReopened() {
        lastLocationSyncTime = 0L
        cachedLat = 0.0
        cachedLon = 0.0
        cachedSpeed = 0f
        cachedAddress = "Unknown"
        cachedWeather = "Clear"
        cachedActivityType = 4
        noiseHistory.clear()
        _isManualOverride.value = false
        // Reset state so it's "like new" while we calculate
        _currentState.value = WidgetState.DISCOVERY
        
        viewModelScope.launch(Dispatchers.IO) {
            syncContext()
        }
    }

    private var lastLocationSyncTime = 0L
    private var cachedLat = 0.0
    private var cachedLon = 0.0
    private var cachedSpeed = 0f
    private var cachedAddress = "Unknown"
    private var cachedWeather = "Clear"
    private var cachedActivityType = 4

    private suspend fun syncContext() {
        if (!syncMutex.tryLock()) return
        try {
            val battery = getBatteryPercentage()
            val isOffline = isNetworkOffline()
            val networkName = getNetworkTypeString()
            val hasFocus = hasUpcomingEvent()
            
            val nowMs = System.currentTimeMillis()
            if (nowMs - lastLocationSyncTime > 5 * 60 * 1000) { // Sync location & weather every 5 mins
                if (hasLocationPermission()) {
                    val location = getLocation()
                    if (location != null) {
                        cachedLat = location.latitude
                        cachedLon = location.longitude
                        cachedSpeed = location.speed * 2.23694f
                        cachedAddress = getAddressName(cachedLat, cachedLon)
                        cachedWeather = getWeather(cachedLat, cachedLon)
                    }
                }
                lastLocationSyncTime = nowMs
            }
            
            cachedActivityType = com.example.ActivityGlobalState.detectedActivity.value
            if (cachedActivityType == 4 || cachedActivityType == 3) {
                if (cachedSpeed >= 20.0) cachedActivityType = 0
                else if (cachedSpeed >= 12.0) cachedActivityType = 1
                else if (cachedSpeed >= 5.0) cachedActivityType = 8
                else if (cachedSpeed >= 1.5) cachedActivityType = 2
            }
            
            val weather = cachedWeather
            val timeHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
            val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
            val ambientNoiseLevel = getAmbientNoiseLevel()
            
            noiseHistory.add(ambientNoiseLevel)
            if (noiseHistory.size > 6) {
                noiseHistory.removeAt(0)
            }
            val sustainedLoudNoise = noiseHistory.size >= 6 && noiseHistory.all { it >= 75.0 }

            val actualNetworkSsid = getWifiSsid()
            val isCafe = checkInternetCafe(actualNetworkSsid, ambientNoiseLevel)
            val isHeadphonesConnected = isHeadphonesConnected()

            val newCtx = AtmosphereContext(
                battery = battery,
                isOffline = isOffline,
                networkName = if (actualNetworkSsid != "UNKNOWN") actualNetworkSsid else networkName,
                hasFocusEvent = hasFocus,
                speedMph = cachedSpeed,
                address = cachedAddress,
                weather = weather,
                latPattern = if (cachedLat != 0.0) String.format("LAT: %.4f° N", cachedLat) else "LAT: --",
                timeHour = timeHour,
                ambientNoiseLevel = ambientNoiseLevel,
                sustainedLoudNoise = sustainedLoudNoise,
                detectedActivity = cachedActivityType,
                isInternetCafe = isCafe,
                isHeadphonesConnected = isHeadphonesConnected,
                isWeekend = isWeekend
            )
            _atmosphereContext.value = newCtx
            
            if (_isManualOverride.value && System.currentTimeMillis() > overrideEndTime) {
                _isManualOverride.value = false
            }
            
            val evaluated = evaluateState(newCtx)
            if (!_isManualOverride.value || evaluated == WidgetState.GHOST) {
                _currentState.value = evaluated
            }
        } catch (e: Throwable) {
            // Ignored to prevent crashes in the background loop
        } finally {
            syncMutex.unlock()
        }
    }

    private var isAudioDisabled = false

    @SuppressLint("MissingPermission")
    private suspend fun getAmbientNoiseLevel(): Double {
        if (!isAppInForeground || isAudioDisabled) {
            return 0.0
        }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return 0.0
        }
        val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_RECORD_AUDIO, android.os.Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_RECORD_AUDIO, android.os.Process.myUid(), ctx.packageName)
        }
        if (mode != android.app.AppOpsManager.MODE_ALLOWED) {
            return 0.0
        }
        return try {
            val sampleRate = 8000
            val channelConfig = android.media.AudioFormat.CHANNEL_IN_MONO
            val audioFormat = android.media.AudioFormat.ENCODING_PCM_16BIT
            val minBufSize = android.media.AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            
            val audioRecord = android.media.AudioRecord(
                android.media.MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                minBufSize
            )

            if (audioRecord.state != android.media.AudioRecord.STATE_INITIALIZED) {
                audioRecord.release()
                isAudioDisabled = true
                return 0.0
            }

            var maxAmplitude = 0
            try {
                audioRecord.startRecording()
                if (audioRecord.recordingState != android.media.AudioRecord.RECORDSTATE_RECORDING) {
                    isAudioDisabled = true
                    return 0.0
                }
                val buffer = ShortArray(minBufSize)
                
                val endTime = System.currentTimeMillis() + 1000 // 1 second
                while (System.currentTimeMillis() < endTime) {
                    val readSize = audioRecord.read(buffer, 0, minBufSize)
                    if (readSize > 0) {
                        for (i in 0 until readSize) {
                            val amplitude = kotlin.math.abs(buffer[i].toInt())
                            if (amplitude > maxAmplitude) {
                                maxAmplitude = amplitude
                            }
                        }
                    }
                    delay(50)
                }
            } finally {
                try {
                    audioRecord.stop()
                } catch (e: Exception) {}
                audioRecord.release()
            }
            
            if (maxAmplitude > 0) {
                20 * kotlin.math.log10(maxAmplitude.toDouble())
            } else {
                0.0
            }
        } catch (e: Throwable) {
            isAudioDisabled = true
            0.0
        }
    }

    private fun getWifiSsid(): String {
        return try {
            val wifiManager = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wifiManager.connectionInfo
            val ssid = info.ssid
            if (ssid != null && ssid.isNotEmpty() && ssid != "<unknown ssid>") {
                ssid.replace("\"", "")
            } else {
                "UNKNOWN"
            }
        } catch (e: Exception) {
            "UNKNOWN"
        }
    }

    private fun getBatteryPercentage(): Int {
        return try {
            val batteryStatus = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            batteryStatus?.let {
                val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (scale > 0) (level * 100 / scale.toFloat()).toInt() else 100
            } ?: 100
        } catch (e: Exception) {
            100
        }
    }

    private fun isNetworkOffline(): Boolean {
        return try {
            val connectivityManager = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return true
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return true
            !(activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                     activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                     activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
        } catch (e: Exception) {
            true
        }
    }

    private fun getNetworkTypeString(): String {
        return try {
            val connectivityManager = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return "OFFLINE"
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return "OFFLINE"
            if (activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "WIFI"
            if (activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "5G ULTRA"
            "UNKNOWN"
        } catch (e: Exception) {
            "UNKNOWN"
        }
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun getLocation(): Location? {
        return try {
            val locationManager = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) 
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            null
        }
    }

    private fun getAddressName(lat: Double, lon: Double): String {
        return try {
            val geocoder = android.location.Geocoder(ctx, java.util.Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                addresses?.get(0)?.thoroughfare ?: addresses?.get(0)?.locality ?: "City"
            } else "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun hasUpcomingEvent(): Boolean {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        return try {
            val projection = arrayOf(CalendarContract.Instances.TITLE)
            val now = System.currentTimeMillis()
            val nextHour = now + 1000 * 60 * 60 // 1 hour from now
            
            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            android.content.ContentUris.appendId(builder, now)
            android.content.ContentUris.appendId(builder, nextHour)
            
            ctx.contentResolver.query(
                builder.build(), projection, null, null, null
            )?.use { cursor ->
                val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                while (cursor.moveToNext()) {
                    val title = cursor.getString(titleIndex)?.lowercase() ?: ""
                    if (title.contains("class") || title.contains("meeting") || title.contains("work")) {
                        return true
                    }
                }
                false
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun getWeather(lat: Double, lon: Double): String {
        var connection: java.net.HttpURLConnection? = null
        return try {
            val url = java.net.URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=weather_code")
            connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val match = """"weather_code":\s*(\d+)""".toRegex().find(response)
                if (match != null) {
                    when (match.groupValues[1].toInt()) {
                        in 51..67 -> "Rain"
                        in 71..77 -> "Snow"
                        in 80..82 -> "Rain"
                        in 95..99 -> "Storm"
                        else -> "Clear"
                    }
                } else "Clear"
            } else "Clear"
        } catch (e: Exception) {
            "Clear"
        } finally {
            connection?.disconnect()
        }
    }

    private val prefs = ctx.getSharedPreferences("AtmospherePrefs", Context.MODE_PRIVATE)

    fun getSavedAppForState(state: WidgetState): String? {
        return prefs.getString("app_${state.name}", null)
    }

    fun saveAppForState(state: WidgetState, link: String) {
        prefs.edit().putString("app_${state.name}", link).apply()
    }

    override fun onCleared() {
        super.onCleared()
    }
}
