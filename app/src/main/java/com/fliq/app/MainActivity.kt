package com.fliq.app

import android.Manifest
import android.app.AppOpsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.fliq.app.service.FliqVpnService
import com.fliq.app.ui.theme.FliqTheme

class MainActivity : ComponentActivity() {

    private var isFliqActive = mutableStateOf(false)
    private var hasUsagePermission = mutableStateOf(false)
    private var networkName = mutableStateOf("—")
    private var signalStrength = mutableStateOf(0)
    private var activeApp = mutableStateOf("—")

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) startFliq()
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) {
            Log.d("MainActivity", "Location permission granted — WiFi name now readable")
        }
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            networkName.value = intent.getStringExtra("networkName") ?: "—"
            signalStrength.value = intent.getIntExtra("signalStrength", 0)
            activeApp.value = intent.getStringExtra("activeApp") ?: "—"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasUsagePermission.value = checkUsagePermission()
        requestLocationPermission()
        setContent {
            FliqTheme {
                FliqHomeScreen(
                    isActive = isFliqActive.value,
                    hasUsagePermission = hasUsagePermission.value,
                    networkName = networkName.value,
                    signalStrength = signalStrength.value,
                    activeApp = activeApp.value,
                    onToggle = { toggleFliq() },
                    onGrantPermission = {
                        startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasUsagePermission.value = checkUsagePermission()
        registerReceiver(
            statusReceiver,
            IntentFilter("com.fliq.app.STATUS_UPDATE"),
            RECEIVER_NOT_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(statusReceiver) } catch (e: Exception) { }
    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun checkUsagePermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(), packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun toggleFliq() {
        if (isFliqActive.value) stopFliq() else requestVpnPermission()
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) vpnPermissionLauncher.launch(intent) else startFliq()
    }

    private fun startFliq() {
        startForegroundService(Intent(this, FliqVpnService::class.java).apply {
            action = FliqVpnService.ACTION_START
        })
        isFliqActive.value = true
    }

    private fun stopFliq() {
        startService(Intent(this, FliqVpnService::class.java).apply {
            action = FliqVpnService.ACTION_STOP
        })
        isFliqActive.value = false
        networkName.value = "—"
        signalStrength.value = 0
        activeApp.value = "—"
    }
}

@Composable
fun FliqHomeScreen(
    isActive: Boolean,
    hasUsagePermission: Boolean,
    networkName: String,
    signalStrength: Int,
    activeApp: String,
    onToggle: () -> Unit,
    onGrantPermission: () -> Unit
) {
    val accentColor = if (isActive) Color(0xFF00E676) else Color(0xFF333333)
    val animatedAccent by animateColorAsState(
        targetValue = accentColor,
        animationSpec = tween(600), label = "accent"
    )
    val buttonScale by animateFloatAsState(
        targetValue = if (isActive) 1.05f else 1f,
        animationSpec = tween(300), label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "fliq",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 6.sp
            )

            Text(
                text = if (isActive) "Your network is optimized" else "Tap to activate Fliq",
                fontSize = 14.sp,
                color = if (isActive) Color(0xFF00E676) else Color(0xFF666666)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .size(180.dp)
                    .scale(buttonScale)
                    .clip(CircleShape)
                    .background(animatedAccent),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = onToggle,
                    modifier = Modifier.size(180.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    ),
                    elevation = null
                ) {
                    Text(
                        text = if (isActive) "ON" else "OFF",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) Color(0xFF0A0A0A) else Color(0xFF666666)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(label = "Network", value = networkName, isActive = isActive)
                StatCard(
                    label = "Signal",
                    value = if (isActive) signalBars(signalStrength) else "—",
                    isActive = isActive
                )
                StatCard(label = "Active app", value = activeApp, isActive = isActive)
            }

            if (!hasUsagePermission) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1A1A1A)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Enable app detection",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "Fliq needs permission to detect active apps and prioritize bandwidth for calls and streams.",
                            fontSize = 12.sp,
                            color = Color(0xFF888888),
                            textAlign = TextAlign.Start
                        )
                        Button(
                            onClick = onGrantPermission,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00E676)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "Grant Permission",
                                color = Color(0xFF0A0A0A),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, isActive: Boolean) {
    Card(
        modifier = Modifier.width(110.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A1A)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = Color(0xFF666666)
            )
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isActive) Color(0xFF00E676) else Color(0xFF444444),
                maxLines = 1
            )
        }
    }
}

fun signalBars(strength: Int): String {
    return when (strength) {
        0 -> "▂___"
        1 -> "▂▄__"
        2 -> "▂▄▆_"
        3 -> "▂▄▆█"
        4 -> "▂▄▆█"
        else -> "—"
    }
}