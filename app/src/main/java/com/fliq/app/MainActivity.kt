package com.fliq.app

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fliq.app.service.FliqVpnService
import com.fliq.app.ui.theme.FliqTheme

class MainActivity : ComponentActivity() {

    private var isFliqActive = mutableStateOf(false)
    private var hasUsagePermission = mutableStateOf(false)

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startFliq()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasUsagePermission.value = checkUsagePermission()
        setContent {
            FliqTheme {
                FliqHomeScreen(
                    isActive = isFliqActive.value,
                    hasUsagePermission = hasUsagePermission.value,
                    onToggle = { toggleFliq() },
                    onGrantPermission = { requestUsagePermission() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasUsagePermission.value = checkUsagePermission()
    }

    private fun checkUsagePermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestUsagePermission() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun toggleFliq() {
        if (isFliqActive.value) {
            stopFliq()
        } else {
            requestVpnPermission()
        }
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startFliq()
        }
    }

    private fun startFliq() {
        val intent = Intent(this, FliqVpnService::class.java).apply {
            action = FliqVpnService.ACTION_START
        }
        startForegroundService(intent)
        isFliqActive.value = true
    }

    private fun stopFliq() {
        val intent = Intent(this, FliqVpnService::class.java).apply {
            action = FliqVpnService.ACTION_STOP
        }
        startService(intent)
        isFliqActive.value = false
    }
}

@Composable
fun FliqHomeScreen(
    isActive: Boolean,
    hasUsagePermission: Boolean,
    onToggle: () -> Unit,
    onGrantPermission: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Text(
                text = "fliq",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 4.sp
            )

            Text(
                text = if (isActive) "Network optimized" else "Tap to activate",
                fontSize = 16.sp,
                color = if (isActive) Color(0xFF00E676) else Color(0xFF888888)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Power button
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) Color(0xFF00E676) else Color(0xFF1E1E1E)
                    ),
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
                        color = if (isActive) Color(0xFF0A0A0A) else Color(0xFF555555)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Status cards
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusCard(
                    title = "Network",
                    value = if (isActive) "Optimal" else "—",
                    isActive = isActive
                )
                StatusCard(
                    title = "Background",
                    value = if (isActive) "Throttled" else "—",
                    isActive = isActive
                )
            }

            // Usage permission banner
            if (!hasUsagePermission) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1A1A2E)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Enable app detection",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Let Fliq detect when you're on a video call or streaming to prioritize your bandwidth.",
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
                                text = "Grant Permission",
                                color = Color(0xFF0A0A0A),
                                fontSize = 13.sp,
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
fun StatusCard(title: String, value: String, isActive: Boolean) {
    Card(
        modifier = Modifier.width(150.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                color = Color(0xFF888888)
            )
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isActive) Color(0xFF00E676) else Color(0xFF555555)
            )
        }
    }
}