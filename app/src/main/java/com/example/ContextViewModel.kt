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
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
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
    val detectedActivity: Int = DetectedActivity.UNKNOWN,
    val isInternetCafe: Boolean = false,
    val isHeadphonesConnected: Boolean = false
)

fun evaluateState(ctx: AtmosphereContext): WidgetState {
    if (ctx.isOffline || ctx.battery < 15) return WidgetState.GHOST
    if (ctx.address.contains("Party", true) || ctx.isSocialMode || ctx.sustainedLoudNoise) return WidgetState.CROWD
    
    // Gym + Headphones -> BEAST, Gym without Headphones -> DISCOVERY (handled by fallback if no other matched)
    if ((ctx.address.contains("Gym", true) && ctx.isHeadphonesConnected) || ctx.detectedActivity == DetectedActivity.RUNNING || ctx.detectedActivity == DetectedActivity.ON_BICYCLE) return WidgetState.BEAST
    
    if ((ctx.timeHour >= 18 && (ctx.battery < 50 || (ctx.ambientNoiseLevel > 0.0 && ctx.ambientNoiseLevel <= 50.0))) || (ctx.address.contains("Home", true) && ctx.timeHour >= 18)) return WidgetState.REWIND
    if (ctx.hasFocusEvent || ctx.isInternetCafe || (ctx.ambientNoiseLevel > 0.0 && ctx.ambientNoiseLevel <= 50.0)) return WidgetState.FOCUS
    if (ctx.weather == "Rain" || ctx.weather == "Storm" || ctx.weather == "Snow") return WidgetState.COZY
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
    private var currentActivityType = DetectedActivity.UNKNOWN
    
    // Register receiver for activity updates
    private val activityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ActivityRecognitionResult.hasResult(intent)) {
                val result = ActivityRecognitionResult.extractResult(intent)
                result?.let {
                    currentActivityType = it.mostProbableActivity.type
                }
            }
        }
    }

    init {
        // Register Activity Recognition
        ContextCompat.registerReceiver(
            ctx,
            activityReceiver,
            IntentFilter("ACTION_PROCESS_ACTIVITY_TRANSITIONS"),
            ContextCompat.RECEIVER_EXPORTED
        )

        try {
            val pendingIntent = PendingIntent.getBroadcast(
                ctx, 0, Intent("ACTION_PROCESS_ACTIVITY_TRANSITIONS").apply {
                    setPackage(ctx.packageName)
                }, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            ActivityRecognition.getClient(ctx).requestActivityUpdates(10000, pendingIntent)
        } catch (e: Exception) {}

        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                syncContext()
                delay(10_000)
            }
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

    private suspend fun syncContext() {
        try {
            val battery = getBatteryPercentage()
            val isOffline = isNetworkOffline()
            val networkName = getNetworkTypeString()
            val hasFocus = hasUpcomingEvent()
            
            var lat = 0.0
            var lon = 0.0
            var speedMph = 0f
            var addressName = "Unknown"

            if (hasLocationPermission()) {
                val location = getLocation()
                if (location != null) {
                    lat = location.latitude
                    lon = location.longitude
                    speedMph = location.speed * 2.23694f
                    addressName = getAddressName(lat, lon)
                }
            }

            val weather = if (lat != 0.0 && lon != 0.0) getWeather(lat, lon) else "Clear"
            val timeHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
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
                speedMph = speedMph,
                address = addressName,
                weather = weather,
                latPattern = if (lat != 0.0) String.format("LAT: %.4f° N", lat) else "LAT: --",
                timeHour = timeHour,
                ambientNoiseLevel = ambientNoiseLevel,
                sustainedLoudNoise = sustainedLoudNoise,
                detectedActivity = currentActivityType,
                isInternetCafe = isCafe,
                isHeadphonesConnected = isHeadphonesConnected
            )
            _atmosphereContext.value = newCtx
            
            if (_isManualOverride.value && System.currentTimeMillis() > overrideEndTime) {
                _isManualOverride.value = false
            }
            
            val evaluated = evaluateState(newCtx)
            if (!_isManualOverride.value || evaluated == WidgetState.GHOST) {
                _currentState.value = evaluated
            }
        } catch (e: Exception) {
            // Ignored to prevent crashes in the background loop
        }
    }

    private suspend fun getAmbientNoiseLevel(): Double {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return 0.0
        }
        var recorder: android.media.MediaRecorder? = null
        var tempFile: java.io.File? = null
        return try {
            tempFile = java.io.File.createTempFile("noise", ".3gp", ctx.cacheDir)
            recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                android.media.MediaRecorder(ctx)
            } else {
                @Suppress("DEPRECATION")
                android.media.MediaRecorder()
            }
            recorder.apply {
                setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                setOutputFormat(android.media.MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(tempFile.absolutePath)
                prepare()
                start()
            }
            recorder.maxAmplitude // First call returns 0, resets baseline
            delay(1500) // Sample for 1.5 seconds
            val amplitude = recorder.maxAmplitude
            recorder.stop()
            recorder.release()
            tempFile.delete()
            
            if (amplitude > 0) {
                20 * kotlin.math.log10(amplitude.toDouble())
            } else {
                0.0
            }
        } catch (e: Exception) {
            try {
                recorder?.release()
                tempFile?.delete()
            } catch (ex: Exception) {
                // ignore
            }
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
}
