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
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.audiobtbridge.latency.LatencyMode
import com.example.audiobtbridge.ui.components.*
import com.example.audiobtbridge.ui.theme.MobileSpeakerTheme
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("AS2P_Prefs", Context.MODE_PRIVATE)
        val savedModeName = prefs.getString("latency_mode", LatencyMode.BALANCE.name)
        val initialMode = try { LatencyMode.valueOf(savedModeName!!) } catch (_: Exception) { LatencyMode.BALANCE }
        AudioService.setModeOffline(initialMode)

        val initialPlc = prefs.getBoolean("plc_enabled", true)
        AudioService.updatePlcEnabled(initialPlc)

        setContent {
            MobileSpeakerTheme {
                MainScreen(this)
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

private fun startAudioWithTarget(context: Context, serverIp: String?, token: String?) {
    val intent = Intent(context, AudioService::class.java).apply {
        action = AudioService.ACTION_START
        if (!serverIp.isNullOrBlank()) putExtra(AudioService.EXTRA_SERVER_IP, serverIp)
        if (!token.isNullOrBlank()) putExtra(AudioService.EXTRA_PAIR_TOKEN, token)
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
    var savedPcs by remember { mutableStateOf(SavedPcStore.loadSavedPcs(context)) }
    var activePcIp by remember { mutableStateOf(prefs.getString("last_server_ip", savedPcs.firstOrNull()?.ip)) }
    var activePcName by remember { mutableStateOf(prefs.getString("last_server_name", savedPcs.firstOrNull()?.hostName)) }
    var activePairToken by remember { mutableStateOf(prefs.getString("last_pair_token", savedPcs.firstOrNull()?.token)) }

    var isPairingBusy by remember { mutableStateOf(false) }
    var pairingStatusText by remember { mutableStateOf<String?>(null) }
    var activeChallenge by remember { mutableStateOf<PairChallenge?>(null) }
    var showTuningSheet by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    fun completePairingSuccess(ip: String, hostName: String, token: String) {
        savedPcs = SavedPcStore.savePairedPc(context, ip, hostName, token)
        activePcIp = ip
        activePcName = hostName
        activePairToken = token
        pairingStatusText = "Paired with $hostName ($ip)"
        startAudioWithTarget(context, ip, token)
    }

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
                is PairResult.Success -> completePairingSuccess(res.serverIp, res.hostName, res.token)
                is PairResult.Failure -> pairingStatusText = res.reason
            }
        }
    }

    fun launchQrScanner() {
        val options = ScanOptions().apply {
            setPrompt("Scan QR code in Mobile Speaker on your PC")
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
            pairingStatusText = "Camera denied — use Match Code instead."
        }
    }

    activeChallenge?.let { challenge ->
        NumberMatchDialog(
            challenge = challenge,
            isBusy = isPairingBusy,
            onSelectChoice = { num ->
                isPairingBusy = true
                scope.launch {
                    val res = PairingClient.verifyChallenge(challenge, num)
                    isPairingBusy = false
                    activeChallenge = null
                    when (res) {
                        is PairResult.Success -> completePairingSuccess(res.serverIp, res.hostName, res.token)
                        is PairResult.Failure -> pairingStatusText = res.reason
                    }
                }
            },
            onDismiss = { activeChallenge = null }
        )
    }

    if (showTuningSheet) {
        AudioTuningBottomSheet(
            initialBitrate = prefs.getInt("adv_bitrate", 160000),
            initialComplexity = prefs.getInt("adv_complexity", 8),
            initialBufferSize = prefs.getInt("adv_buffer_size", 4),
            initialPcmTarget = prefs.getInt("adv_pcm_target", 2),
            initialCatchup = prefs.getInt("adv_catchup", 1),
            initialPlcEnabled = prefs.getBoolean("plc_enabled", true),
            onApply = { br, comp, buf, pcmT, cup, plc ->
                prefs.edit()
                    .putString("latency_mode", LatencyMode.CUSTOM_SETTING.name)
                    .putInt("adv_bitrate", br)
                    .putInt("adv_complexity", comp)
                    .putInt("adv_buffer_size", buf)
                    .putInt("adv_pcm_target", pcmT)
                    .putInt("adv_catchup", cup)
                    .putBoolean("plc_enabled", plc)
                    .apply()
                AudioService.setModeOffline(LatencyMode.CUSTOM_SETTING)
                AudioService.updatePlcEnabled(plc)
                AudioService.applyAdvancedSettings(br, comp, buf, pcmT, cup)
                showTuningSheet = false
            },
            onDismiss = { showTuningSheet = false }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Mobile Speaker",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { openTetheringSettings(context) }) {
                        Icon(
                            imageVector = SpeakerIcons.Usb,
                            contentDescription = "USB Tethering Settings"
                        )
                    }
                    IconButton(onClick = { showTuningSheet = true }) {
                        Icon(
                            imageVector = SpeakerIcons.Tune,
                            contentDescription = "Audio Tuning"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeroStatusCard(
                serviceState = serviceState,
                isSearching = isSearching,
                latencyMs = latency,
                history = history,
                connectedIp = connectedIp,
                activePcName = activePcName,
                onToggleStream = {
                    val intent = Intent(context, AudioService::class.java).apply {
                        action = if (serviceState) AudioService.ACTION_STOP else AudioService.ACTION_START
                        if (!serviceState && !activePcIp.isNullOrBlank()) {
                            putExtra(AudioService.EXTRA_SERVER_IP, activePcIp)
                            putExtra(AudioService.EXTRA_PAIR_TOKEN, activePairToken)
                        }
                    }
                    if (serviceState) context.startService(intent) else context.startForegroundService(intent)
                }
            )

            QuickPairCard(
                isPairingBusy = isPairingBusy,
                pairingRequired = pairingRequired,
                pairingStatusText = pairingStatusText,
                onScanQr = {
                    val hasCam = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasCam) launchQrScanner() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                },
                onNumberMatch = {
                    isPairingBusy = true
                    pairingStatusText = "Searching Wi-Fi & Hotspot for PC..."
                    scope.launch {
                        val challenges = PairingClient.discoverChallenges(context, activePcIp)
                        isPairingBusy = false
                        if (challenges.isEmpty()) {
                            pairingStatusText = "No PC found. Try Scan QR or check Wi-Fi / Hotspot."
                        } else {
                            pairingStatusText = null
                            activeChallenge = challenges.first()
                        }
                    }
                }
            )

            SavedPcsSection(
                savedPcs = savedPcs,
                activeIp = connectedIp ?: activePcIp,
                onConnectPc = { pc ->
                    savedPcs = SavedPcStore.savePairedPc(context, pc.ip, pc.hostName, pc.token)
                    activePcIp = pc.ip
                    activePcName = pc.hostName
                    activePairToken = pc.token
                    pairingStatusText = "Connecting to ${pc.hostName}..."
                    startAudioWithTarget(context, pc.ip, pc.token)
                },
                onDeletePc = { pc ->
                    savedPcs = SavedPcStore.deleteSavedPc(context, pc.ip)
                    if (activePcIp == pc.ip) {
                        activePcIp = savedPcs.firstOrNull()?.ip
                        activePcName = savedPcs.firstOrNull()?.hostName
                        activePairToken = savedPcs.firstOrNull()?.token
                    }
                }
            )

            QualityPresetSection(
                currentMode = currentMode,
                onSelectMode = { mode ->
                    prefs.edit().putString("latency_mode", mode.name).apply()
                    AudioService.setModeOffline(mode)
                    val intent = Intent(context, AudioService::class.java).apply {
                        action = AudioService.ACTION_UPDATE_MODE
                        putExtra(AudioService.EXTRA_MODE, mode.name)
                    }
                    context.startService(intent)
                },
                onOpenTuningSheet = { showTuningSheet = true }
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
