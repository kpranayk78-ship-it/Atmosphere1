package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.lifecycle.viewmodel.compose.viewModel
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

@Composable
fun AtmosphereWidgetApp(modifier: Modifier = Modifier, viewModel: ContextViewModel = viewModel()) {
    val currentState by viewModel.currentState.collectAsState()
    val ctxState by viewModel.atmosphereContext.collectAsState()

    var isPrivacyOn by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

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
                        onCheckedChange = { isPrivacyOn = it }, 
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
                PixelIcon(currentState.pixels, modifier = Modifier.size(80.dp).alpha(0.9f))
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
                    WidgetState.BEAST -> if (ctxState.detectedActivity == com.google.android.gms.location.DetectedActivity.RUNNING) "Context: Running" else if (ctxState.detectedActivity == com.google.android.gms.location.DetectedActivity.ON_BICYCLE) "Context: Cycling" else "Context: Gym / HP ${if(ctxState.isHeadphonesConnected) "ON" else "OFF"}"
                    WidgetState.REWIND -> "Context: ${ctxState.address} @ ${ctxState.timeHour}H"
                    WidgetState.COZY -> "Context: Weather ${ctxState.weather}"
                    WidgetState.CROWD -> if (ctxState.sustainedLoudNoise) "Context: Loud (${ctxState.ambientNoiseLevel.toInt()}dB sustained)" else "Context: Party / Social"
                    WidgetState.DISCOVERY -> "Context: Default Flow"
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
                    .clickable { viewModel.overrideState() }
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

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .shadow(16.dp, CircleShape, spotColor = Color(0xFFD71921))
                        .background(Color(0xFFD71921), CircleShape)
                        .clickable { 
                            launchMediaLink(context, currentState.link)
                         },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play", modifier = Modifier.size(40.dp), tint = Color.White)
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
    }
}
