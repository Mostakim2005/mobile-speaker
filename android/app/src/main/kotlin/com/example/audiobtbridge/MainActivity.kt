package com.example.audiobtbridge

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.audiobtbridge.latency.LatencyMode
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("AS2P_Prefs", Context.MODE_PRIVATE)
        val savedModeName = prefs.getString("latency_mode", LatencyMode.BALANCE.name)
        val initialMode = try { LatencyMode.valueOf(savedModeName!!) } catch (e: Exception) { LatencyMode.BALANCE }
        AudioService.setModeOffline(initialMode)

        val initialPlc = prefs.getBoolean("plc_enabled", true)
        AudioService.updatePlcEnabled(initialPlc)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF64FFDA),
                    surface = Color(0xFF121212),
                    background = Color(0xFF0A0A0A),
                    error = Color(0xFFFF5252)
                )
            ) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MainScreen(this)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val intent = Intent(this, AudioService::class.java).apply { action = AudioService.ACTION_UI_VISIBLE }
        startService(intent)
    }

    override fun onPause() {
        super.onPause()
        val intent = Intent(this, AudioService::class.java).apply { action = AudioService.ACTION_UI_HIDDEN }
        startService(intent)
    }
}

@Composable
fun LatencyChart(history: List<Float>, modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .height(110.dp)
            .fillMaxWidth()
            .background(Color.Black.copy(0.3f), RoundedCornerShape(16.dp))
            .padding(8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (history.size < 2) return@Canvas
            val maxLatency = 200f
            val width = size.width
            val height = size.height
            val stepX = width / 60f
            val path = Path()
            history.takeLast(60).forEachIndexed { index, value ->
                val x = index * stepX
                val y = height - (value.coerceIn(0f, maxLatency) / maxLatency * height)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path = path, color = primaryColor, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

private fun openTetheringSettings(context: Context) {
    val actions = listOf(
        "android.settings.TETHER_SETTINGS",
        Settings.ACTION_WIRELESS_SETTINGS,
        Settings.ACTION_SETTINGS
    )
    for (action in actions) {
        try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {}
    }
    Toast.makeText(context, "Could not open tethering settings", Toast.LENGTH_SHORT).show()
}

private fun startPairedAudioService(context: Context, serverIp: String, hostName: String, token: String) {
    val prefs = context.getSharedPreferences("AS2P_Prefs", Context.MODE_PRIVATE)
    prefs.edit()
        .putString("last_server_ip", serverIp)
        .putString("last_server_name", hostName)
        .putString("last_pair_token", token)
        .apply()

    val intent = Intent(context, AudioService::class.java).apply {
        action = AudioService.ACTION_START
        putExtra(AudioService.EXTRA_SERVER_IP, serverIp)
        putExtra(AudioService.EXTRA_PAIR_TOKEN, token)
    }
    context.startForegroundService(intent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(context: Context) {
    val serviceState by AudioService.serviceState.collectAsState()
    val latency by AudioService.latencyFlow.collectAsState()
    val history by AudioService.latencyHistoryFlow.collectAsState()
    val currentMode by AudioService.latencyModeFlow.collectAsState()
    val isSearching by AudioService.isSearchingFlow.collectAsState()
    val connectedIp by AudioService.connectedServerFlow.collectAsState()
    val pairingRequired by AudioService.pairingRequiredFlow.collectAsState()

    val prefs = remember { context.getSharedPreferences("AS2P_Prefs", Context.MODE_PRIVATE) }
    var pairedIp by remember { mutableStateOf(prefs.getString("last_server_ip", null)) }
    var pairedName by remember { mutableStateOf(prefs.getString("last_server_name", null)) }
    var isPairingBusy by remember { mutableStateOf(false) }
    var pairingStatusText by remember { mutableStateOf<String?>(null) }
    var activeChallenge by remember { mutableStateOf<PairChallenge?>(null) }

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val qrLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents ?: return@rememberLauncherForActivityResult
        val parsed = PairingProtocol.parseQrPayload(contents)
        if (parsed == null) {
            pairingStatusText = "Not a valid Mobile Speaker QR code."
            return@rememberLauncherForActivityResult
        }
        isPairingBusy = true
        pairingStatusText = "Connecting to ${parsed.hostName}..."
        scope.launch {
            val res = PairingClient.pairViaQr(parsed)
            isPairingBusy = false
            when (res) {
                is PairResult.Success -> {
                    pairedIp = res.serverIp
                    pairedName = res.hostName
                    pairingStatusText = "Paired with ${res.hostName} (${res.serverIp})"
                    startPairedAudioService(context, res.serverIp, res.hostName, res.token)
                }
                is PairResult.Failure -> {
                    pairingStatusText = res.reason
                }
            }
        }
    }

    fun launchQrScanner() {
        val options = ScanOptions().apply {
            setPrompt("Scan QR code on PC Mobile Speaker")
            setBeepEnabled(false)
            setOrientationLocked(false)
        }
        qrLauncher.launch(options)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchQrScanner()
        } else {
            pairingStatusText = "Camera denied — use Number Match instead."
        }
    }

    activeChallenge?.let { challenge ->
        AlertDialog(
            onDismissRequest = { if (!isPairingBusy) activeChallenge = null },
            title = { Text("Match number on ${challenge.hostName}", fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Tap the 2-digit number shown in Mobile Speaker on your PC (${challenge.serverIp}):",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        challenge.choices.forEach { num ->
                            FilledTonalButton(
                                enabled = !isPairingBusy,
                                onClick = {
                                    isPairingBusy = true
                                    scope.launch {
                                        val res = PairingClient.verifyChallenge(challenge, num)
                                        isPairingBusy = false
                                        activeChallenge = null
                                        when (res) {
                                            is PairResult.Success -> {
                                                pairedIp = res.serverIp
                                                pairedName = res.hostName
                                                pairingStatusText = "Paired with ${res.hostName} (${res.serverIp})"
                                                startPairedAudioService(context, res.serverIp, res.hostName, res.token)
                                            }
                                            is PairResult.Failure -> {
                                                pairingStatusText = res.reason
                                            }
                                        }
                                    }
                                },
                                shape = CircleShape,
                                modifier = Modifier.size(68.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(num, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(enabled = !isPairingBusy, onClick = { activeChallenge = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Mobile Speaker",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            OutlinedButton(
                onClick = { openTetheringSettings(context) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            ) {
                Text("USB TETHERING", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Quick Pair Card (QR + Google-Style Number Matching across Wi-Fi & Hotspots)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF181B22))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                val targetLabel = when {
                    connectedIp != null -> "Active: ${pairedName ?: "PC"} ($connectedIp)"
                    pairedIp != null -> "Saved PC: ${pairedName ?: "PC"} ($pairedIp)"
                    else -> "Scan QR or Match Number to Pair PC"
                }
                Text(
                    text = targetLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (connectedIp != null) Color(0xFF4ADE80) else Color(0xFFE2E8F0),
                    fontWeight = FontWeight.Bold
                )
                if (pairingRequired) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "PC Pairing Lock is active — scan QR or tap Number Match.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (pairingStatusText != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = pairingStatusText!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        enabled = !isPairingBusy,
                        onClick = {
                            val hasCam = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasCam) {
                                launchQrScanner()
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("SCAN QR", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        enabled = !isPairingBusy,
                        onClick = {
                            isPairingBusy = true
                            pairingStatusText = "Searching Wi-Fi & Hotspot for PC..."
                            scope.launch {
                                val challenges = PairingClient.discoverChallenges(context, pairedIp)
                                isPairingBusy = false
                                if (challenges.isEmpty()) {
                                    pairingStatusText = "No PC found. Try SCAN QR or check Wi-Fi / Hotspot."
                                } else {
                                    pairingStatusText = null
                                    activeChallenge = challenges.first()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("NUMBER MATCH", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Latency Info Card
        val isSearchingForServer = isSearching && serviceState
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Box {
                Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isSearchingForServer) "SEARCHING FOR SERVER..." else if (serviceState) "ACTIVE" else "IDLE",
                        color = if (isSearchingForServer) MaterialTheme.colorScheme.primary else if (serviceState) Color.Green else Color.Gray
                    )
                    Text(text = "%.1f".format(latency), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                    Text(text = "LATENCY (MS)", style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    LatencyChart(history = history)
                }
                if (isSearchingForServer) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(3.dp).align(Alignment.TopCenter),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val radioOptions = listOf(
            LatencyMode.LOW_LATENCY,
            LatencyMode.BALANCE,
            LatencyMode.HIGH_QUALITY,
            LatencyMode.BEST_QUALITY,
            LatencyMode.CUSTOM_SETTING
        )
        Column(modifier = Modifier.selectableGroup().fillMaxWidth()) {
            radioOptions.forEach { mode ->
                val selected = mode == currentMode
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).selectable(
                        selected = selected,
                        onClick = {
                            prefs.edit().putString("latency_mode", mode.name).apply()
                            AudioService.setModeOffline(mode)
                            val intent = Intent(context, AudioService::class.java).apply {
                                action = AudioService.ACTION_UPDATE_MODE
                                putExtra(AudioService.EXTRA_MODE, mode.name)
                            }
                            context.startService(intent)
                        },
                        role = Role.RadioButton
                    ),
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary.copy(0.1f) else Color.Transparent
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected, onClick = null)
                        Text(
                            text = if (mode == LatencyMode.CUSTOM_SETTING) "CUSTOM SETTING" else mode.name.replace("_", " "),
                            modifier = Modifier.padding(start = 16.dp),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (currentMode == LatencyMode.CUSTOM_SETTING) {
            var showAdvanced by remember { mutableStateOf(false) }

            var advBitrate by remember { mutableStateOf(prefs.getInt("adv_bitrate", 160000)) }
            var advComplexity by remember { mutableStateOf(prefs.getInt("adv_complexity", 8).toFloat()) }
            var advBufferSize by remember { mutableStateOf(prefs.getInt("adv_buffer_size", 4).toFloat()) }
            var advPcmTarget by remember { mutableStateOf(prefs.getInt("adv_pcm_target", 2).toFloat()) }
            var advCatchup by remember { mutableStateOf(prefs.getInt("adv_catchup", 1).toFloat()) }
            var plcEnabled by remember { mutableStateOf(prefs.getBoolean("plc_enabled", true)) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .background(Color.White.copy(0.05f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().selectable(selected = false, onClick = { showAdvanced = !showAdvanced }),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("ADVANCED TUNING", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(if (showAdvanced) "▲" else "▼", color = MaterialTheme.colorScheme.primary)
                }

                if (showAdvanced) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Packet Loss Concealment", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("PLC Off = Audio Drops", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                        Switch(checked = plcEnabled, onCheckedChange = { plcEnabled = it })
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(0.1f))

                    Text("Bitrate: ${advBitrate / 1000}k", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    val bitrates = listOf(32000, 48000, 64000, 96000, 128000, 192000, 256000, 320000)
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        bitrates.forEach { br ->
                            FilterChip(
                                selected = advBitrate == br,
                                onClick = { advBitrate = br },
                                label = { Text("${br / 1000}k") },
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Complexity: ${advComplexity.toInt()}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Slider(value = advComplexity, onValueChange = { advComplexity = it }, valueRange = 0f..10f, steps = 9)

                    Text("Total Buffer Size (Target): ${advBufferSize.toInt()}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Slider(value = advBufferSize, onValueChange = {
                        advBufferSize = it
                        if (advPcmTarget > advBufferSize) advPcmTarget = advBufferSize
                    }, valueRange = 1f..20f, steps = 18)

                    Text("PCM Pre-decode Target: ${advPcmTarget.toInt()}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Recommended: 2. Higher = Better CPU stability, Lower = Larger Jitter Buffer.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Slider(
                        value = advPcmTarget,
                        onValueChange = { advPcmTarget = it },
                        valueRange = 1f..advBufferSize.coerceAtLeast(1f),
                        steps = (advBufferSize.toInt() - 2).coerceAtLeast(0)
                    )

                    Text("Catch-up Threshold: +${advCatchup.toInt()}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Slider(value = advCatchup, onValueChange = { advCatchup = it }, valueRange = 1f..10f, steps = 8)

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            prefs.edit().apply {
                                putInt("adv_bitrate", advBitrate)
                                putInt("adv_complexity", advComplexity.toInt())
                                putInt("adv_buffer_size", advBufferSize.toInt())
                                putInt("adv_pcm_target", advPcmTarget.toInt())
                                putInt("adv_catchup", advCatchup.toInt())
                                putBoolean("plc_enabled", plcEnabled)
                                apply()
                            }
                            AudioService.applyAdvancedSettings(
                                advBitrate, advComplexity.toInt(), advBufferSize.toInt(), advPcmTarget.toInt(), advCatchup.toInt()
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("APPLY CHANGES")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = {
                val isStreaming = serviceState
                val intent = Intent(context, AudioService::class.java).apply {
                    action = if (isStreaming) AudioService.ACTION_STOP else AudioService.ACTION_START
                }
                if (isStreaming) context.startService(intent) else context.startForegroundService(intent)
            },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(30.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (serviceState) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        ) {
            Text(if (serviceState) "STOP" else "CONNECT", fontWeight = FontWeight.Bold)
        }
    }
}
