package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.Manifest
import android.content.Context
import android.os.Vibrator
import android.os.VibrationEffect
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NdDot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class WidgetState(
    val titleTop: String,
    val titleBottom: String,
    val subTop: String,
    val link: String,
    val pixels: List<String>
) {
    GHOST(
        "GHOST", "MODE",
        "Offline Library",
        "content://downloads", PixelIcons.FloppyDisk
    ),
    FOCUS(
        "FOCUS", "OS",
        "Deep Work / DND",
        "spotify:playlist:37i9dQZF1DWZeKCadgRdKQ", PixelIcons.StopSign
    ),
    DEEP_FOCUS(
        "DEEP", "FOCUS",
        "Pair headphones. DND on.",
        "spotify:playlist:37i9dQZF1DWZeKCadgRdKQ", PixelIcons.StopSign // Can be something else, but PixelIcon.StopSign works
    ),
    BEAST(
        "BEAST", "MODE",
        "High BPM / Phonk",
        "spotify:playlist:37i9dQZF1DX76Wlfdnj7AP", PixelIcons.LightningPath
    ),
    REWIND(
        "REWIND", "MODE",
        "Resume Episode",
        "nflx://www.netflix.com/Browse?zb=1", PixelIcons.Moon
    ),
    COZY(
        "COZY", "VIBE",
        "Lo-fi / Jazz",
        "spotify:playlist:37i9dQZF1DWWQRwui0ExPn", PixelIcons.CloudRain
    ),
    CROWD(
        "CROWD", "CONTROL",
        "Global Top 50",
        "spotify:playlist:37i9dQZF1DXcBWIGoYBM5M", PixelIcons.Speaker
    ),
    BEDTIME(
        "BEDTIME", "MODE",
        "Sleep Sounds",
        "spotify:playlist:37i9dQZF1DWZd79rJ6a7cD", PixelIcons.Moon
    ),
    DISCOVERY(
        "DAILY", "DISCOVERY",
        "Fresh Mix",
        "spotify:playlist:37i9dQZF1DX4JAvHpjipBk", PixelIcons.Waveform
    )
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          AtmosphereWidgetApp(modifier = Modifier.padding(innerPadding))
        }
      }
    }
  }
}

fun launchMediaLink(context: android.content.Context, link: String) {
    if (link == "content://downloads") {
        try {
            context.startActivity(Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch(e: Exception) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply {
                     flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            } catch(e: Exception) {}
        }
    } else {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply {
                 flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (e: Exception) {
            try {
                val fallbackUrl = when {
                    link.startsWith("vnd.youtube") -> "https://www.youtube.com/feed/mix"
                    link.startsWith("spotify:playlist:") -> link.replace("spotify:playlist:", "https://open.spotify.com/playlist/")
                    link.startsWith("nflx:") -> "https://www.netflix.com/"
                    else -> null
                }
                if (fallbackUrl != null) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).apply {
                         flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
            } catch(ex: Exception) {}
        }
    }
}

data class AppChoice(val name: String, val link: String)

object AppDeepLinkMappings {
    val ytLinks = mapOf(
        WidgetState.FOCUS to "https://music.youtube.com/search?q=focus+playlist",
        WidgetState.DEEP_FOCUS to "https://music.youtube.com/search?q=deep+focus+playlist",
        WidgetState.BEAST to "https://music.youtube.com/search?q=beastmode+workout+playlist",
        WidgetState.COZY to "https://music.youtube.com/search?q=cozy+lofi+playlist",
        WidgetState.CROWD to "https://music.youtube.com/search?q=top+50+hits",
        WidgetState.BEDTIME to "https://music.youtube.com/search?q=sleep+sounds+playlist",
        WidgetState.DISCOVERY to "https://music.youtube.com/search?q=new+music+discovery"
    )

    val appleLinks = mapOf(
        WidgetState.FOCUS to "https://music.apple.com/us/search?term=focus+playlist",
        WidgetState.DEEP_FOCUS to "https://music.apple.com/us/search?term=deep+focus",
        WidgetState.BEAST to "https://music.apple.com/us/search?term=workout",
        WidgetState.COZY to "https://music.apple.com/us/search?term=lofi+chill",
        WidgetState.CROWD to "https://music.apple.com/us/search?term=todays+hits",
        WidgetState.BEDTIME to "https://music.apple.com/us/search?term=sleep+sounds",
        WidgetState.DISCOVERY to "https://music.apple.com/us/search?term=new+music"
    )

    val scLinks = mapOf(
        WidgetState.FOCUS to "https://soundcloud.com/search/sets?q=study+focus",
        WidgetState.DEEP_FOCUS to "https://soundcloud.com/search/sets?q=deep+focus",
        WidgetState.BEAST to "https://soundcloud.com/search/sets?q=workout",
        WidgetState.COZY to "https://soundcloud.com/search/sets?q=lofi+chill",
        WidgetState.CROWD to "https://soundcloud.com/search/sets?q=pop+party",
        WidgetState.BEDTIME to "https://soundcloud.com/search/sets?q=sleep+sounds",
        WidgetState.DISCOVERY to "https://soundcloud.com/search/sets?q=new+music"
    )
}

fun getAppChoicesForState(state: WidgetState): List<AppChoice> {
    val ytLink = AppDeepLinkMappings.ytLinks[state] ?: "https://music.youtube.com/"
    val appleLink = AppDeepLinkMappings.appleLinks[state] ?: "https://music.apple.com/us/browse"
    val scLink = AppDeepLinkMappings.scLinks[state] ?: "https://soundcloud.com/discover"

    return when(state) {
        WidgetState.REWIND -> listOf(
            AppChoice("Netflix", "nflx://www.netflix.com/Browse?zb=1"),
            AppChoice("Prime Video", "primevideo://"),
            AppChoice("Disney+ Hotstar", "hotstar://"),
            AppChoice("YouTube", "vnd.youtube://")
        )
        WidgetState.GHOST -> listOf(
            AppChoice("Downloads", "content://downloads"),
            AppChoice("Files", "content://")
        )
        else -> listOf(
            AppChoice("Spotify", state.link),
            AppChoice("YouTube Music", ytLink),
            AppChoice("Apple Music", appleLink),
            AppChoice("SoundCloud", scLink)
        )
    }
}

@Composable
fun AtmosphereWidgetApp(modifier: Modifier = Modifier, viewModel: ContextViewModel = viewModel()) {
    val currentState by viewModel.currentState.collectAsState()
    val ctxState by viewModel.atmosphereContext.collectAsState()

    var isPrivacyOn by remember { mutableStateOf(false) }
    var showAppChooserForState by remember { mutableStateOf<WidgetState?>(null) }

    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }

    var isFirstLaunch by remember { mutableStateOf(true) }

    LaunchedEffect(currentState) {
        if (!isFirstLaunch) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val effect = when (currentState) {
                    WidgetState.COZY -> VibrationEffect.createWaveform(longArrayOf(0, 30, 60, 30), -1)
                    WidgetState.FOCUS -> VibrationEffect.createOneShot(15, 200)
                    WidgetState.DEEP_FOCUS -> VibrationEffect.createOneShot(25, 255)
                    WidgetState.BEAST -> VibrationEffect.createWaveform(longArrayOf(0, 20, 50, 40, 50, 60), -1)
                    WidgetState.REWIND -> VibrationEffect.createWaveform(longArrayOf(0, 40, 100, 20), -1)
                    WidgetState.GHOST -> VibrationEffect.createOneShot(10, 50)
                    else -> VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE)
                }
                vibrator.vibrate(effect)
            } else {
                vibrator.vibrate(30)
            }
        }
        isFirstLaunch = false
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { 
        viewModel.onPermissionsGranted()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.setAppForeground(true)
                viewModel.onAppReopened()
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.setAppForeground(false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.RECORD_AUDIO
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            perms.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(vertical = 32.dp, horizontal = 24.dp)
    ) {
        // Top Right Edge Glyph
        Column(
            modifier = Modifier.align(Alignment.CenterEnd).offset(x = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val litPattern = listOf(true, false, true, true, false, false, true, true, false, false)
            litPattern.forEach { lit ->
                Box(
                    Modifier
                        .size(4.dp)
                        .shadow(if (lit) 8.dp else 0.dp, CircleShape, spotColor = Color.White)
                        .background(Color.White.copy(alpha = if (lit) 1f else 0.2f), CircleShape)
                )
            }
        }

        Column(Modifier.fillMaxSize()) {
            // Header
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Switch(
                        checked = isPrivacyOn, 
                        onCheckedChange = { 
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                vibrator.vibrate(VibrationEffect.createOneShot(15, 100))
                            } else {
                                vibrator.vibrate(15)
                            }
                            isPrivacyOn = it 
                        }, 
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White, 
                            checkedTrackColor = Color.White.copy(0.4f), 
                            uncheckedThumbColor = Color.White.copy(0.6f), 
                            uncheckedTrackColor = Color.White.copy(0.1f)
                        ),
                        modifier = Modifier.scale(0.8f)
                    )
                    Text("PRIVACY", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Medium)
                }
                Text("ATMOSPHERE", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
            }
            
            Spacer(Modifier.weight(1f))

            // Center Content
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PixelIcon(currentState.pixels, modifier = Modifier.size(80.dp).alpha(0.9f), monochrome = ctxState.battery < 15)
                Spacer(Modifier.height(32.dp))
                Text(
                    "${currentState.titleTop}\n${currentState.titleBottom}",
                    fontFamily = NdDot,
                    fontSize = 42.sp,
                    lineHeight = 42.sp,
                    textAlign = TextAlign.Center,
                    letterSpacing = 2.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (isPrivacyOn) "---" else currentState.subTop.uppercase(),
                    color = Color.White,
                    fontSize = 18.sp,
                    letterSpacing = 1.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Light
                )
                Spacer(Modifier.height(8.dp))
                val subBottomContext = when (currentState) {
                    WidgetState.GHOST -> "Context: Net Offline / Bat ${ctxState.battery}%"
                    WidgetState.FOCUS -> if (ctxState.isInternetCafe) "Context: Cafe (${ctxState.networkName})" else if (ctxState.ambientNoiseLevel > 0 && ctxState.ambientNoiseLevel <= 50.0) "Context: Quiet (${ctxState.ambientNoiseLevel.toInt()}dB)" else "Context: Class / Meeting"
                    WidgetState.DEEP_FOCUS -> "Context: ${ctxState.ambientNoiseLevel.toInt()}dB + Meeting"
                    WidgetState.BEAST -> if (ctxState.detectedActivity == 8 /* RUNNING */) "Context: Running" else if (ctxState.detectedActivity == 1 /* ON_BICYCLE */) "Context: Cycling" else "Context: Gym / HP ${if(ctxState.isHeadphonesConnected) "ON" else "OFF"}"
                    WidgetState.REWIND -> "Context: ${ctxState.address} @ ${ctxState.timeHour}H"
                    WidgetState.COZY -> "Context: Weather ${ctxState.weather}"
                    WidgetState.CROWD -> if (ctxState.sustainedLoudNoise) "Context: Loud (${ctxState.ambientNoiseLevel.toInt()}dB sustained)" else "Context: Party / Social"
                    WidgetState.BEDTIME -> "Context: Bedtime / BT Connected"
                    WidgetState.DISCOVERY -> {
                        when (ctxState.detectedActivity) {
                            7, 2 -> "Context: Walking"
                            0 -> "Context: Driving"
                            1 -> "Context: Cycling"
                            8 -> "Context: Running"
                            else -> "Context: Default Flow"
                        }
                    }
                }
                Text(
                    subBottomContext.uppercase(),
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 10.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.weight(1f))

            // Footer Interactive
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier
                    .clickable {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            vibrator.vibrate(VibrationEffect.createOneShot(25, 100))
                        } else {
                            vibrator.vibrate(25)
                        }
                        viewModel.overrideState() 
                    }
                    .padding(8.dp)) {
                    Text("MANUAL OVERRIDE", color = Color.White.copy(0.4f), fontSize = 10.sp, letterSpacing = 2.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("WRONG VIBE?", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                         modifier = Modifier.drawBehind {
                             val strokeWidth = 1.dp.toPx()
                             val y = size.height + 4.dp.toPx()
                             drawLine(Color.White.copy(0.4f), Offset(0f, y), Offset(size.width, y), strokeWidth)
                         })
                }

                val isLowBattery = ctxState.battery < 15
                val accentColor = if (isLowBattery) Color(0xFF333333) else Color(0xFFD71921)
                val iconTint = if (isLowBattery) Color.LightGray else Color.White

                val borderModifier = if (isLowBattery) Modifier.border(1.dp, Color.White.copy(0.4f), CircleShape) else Modifier

                val actionModifier = Modifier.pointerInput(currentState) {
                    detectTapGestures(
                        onTap = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                vibrator.vibrate(VibrationEffect.createOneShot(30, 200))
                            } else {
                                vibrator.vibrate(30)
                            }
                            val savedApp = viewModel.getSavedAppForState(currentState)
                            if (savedApp == null) {
                                showAppChooserForState = currentState
                            } else {
                                launchMediaLink(context, savedApp)
                            }
                        },
                        onLongPress = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                vibrator.vibrate(VibrationEffect.createOneShot(40, 255))
                            } else {
                                vibrator.vibrate(40)
                            }
                            showAppChooserForState = currentState
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .shadow(if (isLowBattery) 0.dp else 16.dp, CircleShape, spotColor = accentColor)
                        .background(accentColor, CircleShape)
                        .then(borderModifier)
                        .then(actionModifier),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play", modifier = Modifier.size(40.dp), tint = iconTint)
                }
            }

            // Bottom status
            Row(Modifier.fillMaxWidth().drawBehind {
                    val strokeWidth = 1.dp.toPx()
                    drawLine(Color.White.copy(0.1f), Offset(0f, -16.dp.toPx()), Offset(size.width, -16.dp.toPx()), strokeWidth)
                }.padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(ctxState.latPattern, color = Color.White.copy(0.3f), fontSize = 9.sp, letterSpacing = 2.sp)
                Text("NET: ${ctxState.networkName}", color = Color.White.copy(0.3f), fontSize = 9.sp, letterSpacing = 2.sp)
                val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                Text(timeStr, color = Color.White.copy(0.3f), fontSize = 9.sp, letterSpacing = 2.sp)
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showAppChooserForState != null,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.matchParentSize()
        ) {
            val state = showAppChooserForState ?: currentState
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { showAppChooserForState = null } // Dismiss on background tap
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(32.dp)
                        .border(1.dp, Color.White.copy(0.3f))
                        .background(Color.Black)
                        .padding(24.dp)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {},
                ) {
                    Text(
                        text = "SELECT_SOURCE",
                        color = Color.White.copy(0.4f),
                        fontSize = 10.sp,
                        letterSpacing = 2.sp,
                        fontFamily = NdDot
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "${state.titleTop}_${state.titleBottom}".uppercase(),
                        fontFamily = NdDot,
                        fontSize = 24.sp,
                        color = Color.White,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    val choices = getAppChoicesForState(state)
                    
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        choices.forEachIndexed { index, app ->
                            val savedApp = viewModel.getSavedAppForState(state)
                            val isSelected = savedApp == app.link || (savedApp == null && index == 0)
                            
                            val textColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.4f)
                            val prefix = if (isSelected) ">" else " "
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                            vibrator.vibrate(VibrationEffect.createOneShot(20, 150))
                                        } else {
                                            vibrator.vibrate(20)
                                        }
                                        viewModel.saveAppForState(state, app.link)
                                        showAppChooserForState = null
                                        launchMediaLink(context, app.link)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$prefix ${app.name.uppercase()}",
                                    color = textColor,
                                    fontSize = 18.sp,
                                    fontFamily = NdDot,
                                    letterSpacing = 2.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = "[CANCEL]",
                        color = Color.White.copy(0.4f),
                        fontSize = 14.sp,
                        fontFamily = NdDot,
                        letterSpacing = 2.sp,
                        modifier = Modifier
                            .clickable {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    vibrator.vibrate(VibrationEffect.createOneShot(10, 50))
                                } else {
                                    vibrator.vibrate(10)
                                }
                                showAppChooserForState = null 
                            }
                            .padding(vertical = 8.dp),
                        textAlign = TextAlign.Start
                    )
                }
            }
        }
    }
}
