package com.example.audiobtbridge.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audiobtbridge.PairChallenge
import com.example.audiobtbridge.SavedPc
import com.example.audiobtbridge.latency.LatencyMode
import com.example.audiobtbridge.ui.theme.LocalSpeakerStatusColors
import kotlin.math.roundToInt

@Composable
fun LatencySparkline(
    history: List<Float>,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    Box(
        modifier = modifier
            .height(92.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        if (history.size < 2) {
            Text(
                text = "Live latency waveform appears when streaming",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val maxLatency = 200f
                val w = size.width
                val h = size.height
                val samples = history.takeLast(60)
                val stepX = w / (samples.size - 1).coerceAtLeast(1)

                val strokePath = Path()
                val fillPath = Path()

                samples.forEachIndexed { index, value ->
                    val x = index * stepX
                    val y = h - (value.coerceIn(0f, maxLatency) / maxLatency * h)
                    if (index == 0) {
                        strokePath.moveTo(x, y)
                        fillPath.moveTo(x, h)
                        fillPath.lineTo(x, y)
                    } else {
                        strokePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                    if (index == samples.lastIndex) {
                        fillPath.lineTo(x, h)
                        fillPath.close()
                    }
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.28f),
                            accentColor.copy(alpha = 0.02f)
                        )
                    )
                )
                drawPath(
                    path = strokePath,
                    color = accentColor,
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}

@Composable
fun HeroStatusCard(
    serviceState: Boolean,
    isSearching: Boolean,
    latencyMs: Float,
    history: List<Float>,
    connectedIp: String?,
    activePcName: String?,
    onToggleStream: () -> Unit
) {
    val statusColors = LocalSpeakerStatusColors.current
    val isSearchingForServer = isSearching && serviceState
    val isAdapting = serviceState && !isSearchingForServer && latencyMs > 120f

    val pillBg by animateColorAsState(
        targetValue = when {
            isSearchingForServer -> MaterialTheme.colorScheme.primaryContainer
            isAdapting -> statusColors.adaptingContainer
            serviceState -> statusColors.syncedContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        label = "pillBg"
    )
    val pillFg by animateColorAsState(
        targetValue = when {
            isSearchingForServer -> MaterialTheme.colorScheme.onPrimaryContainer
            isAdapting -> statusColors.onAdaptingContainer
            serviceState -> statusColors.onSyncedContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "pillFg"
    )
    val sparklineAccent = when {
        isAdapting -> statusColors.adapting
        serviceState -> statusColors.synced
        else -> MaterialTheme.colorScheme.primary
    }

    val statusText = when {
        isSearchingForServer -> "Searching for PC..."
        serviceState && latencyMs > 0f -> {
            val label = if (isAdapting) "Adapting" else "Synced"
            "$label · ${latencyMs.roundToInt()} ms"
        }
        serviceState -> "Connected · Syncing"
        else -> "Speaker Idle"
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = pillBg,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                isSearchingForServer -> SpeakerIcons.Wifi
                                serviceState -> SpeakerIcons.GraphicEq
                                else -> SpeakerIcons.Speaker
                            },
                            contentDescription = null,
                            tint = pillFg,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = statusText,
                            color = pillFg,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (serviceState && latencyMs > 0f) {
                    Text(
                        text = "${latencyMs.roundToInt()} ms",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (isSearchingForServer) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(999.dp))
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = when {
                    connectedIp != null -> "Playing from ${activePcName ?: "PC"} ($connectedIp)"
                    activePcName != null -> "Ready to play from $activePcName"
                    else -> "Pair your Windows PC below to start"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(14.dp))

            LatencySparkline(
                history = history,
                accentColor = sparklineAccent
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onToggleStream,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (serviceState) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    contentColor = if (serviceState) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = if (serviceState) SpeakerIcons.Stop else SpeakerIcons.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (serviceState) "Stop Speaker" else "Connect Speaker",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun QuickPairCard(
    isPairingBusy: Boolean,
    pairingRequired: Boolean,
    pairingStatusText: String?,
    onScanQr: () -> Unit,
    onNumberMatch: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Pair a PC",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Works on Wi-Fi, Windows Hotspot, or Phone Hotspot",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (pairingRequired) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = SpeakerIcons.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "PC Pairing Lock is active — scan QR or tap Match Code.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            } else if (!pairingStatusText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = pairingStatusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    enabled = !isPairingBusy,
                    onClick = onScanQr,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = SpeakerIcons.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scan QR", fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    enabled = !isPairingBusy,
                    onClick = onNumberMatch,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = SpeakerIcons.Pin,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Match Code", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun SavedPcsSection(
    savedPcs: List<SavedPc>,
    activeIp: String?,
    onConnectPc: (SavedPc) -> Unit,
    onDeletePc: (SavedPc) -> Unit
) {
    if (savedPcs.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Saved PCs",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            savedPcs.forEach { pc ->
                val isSelected = pc.ip == activeIp
                Surface(
                    onClick = { onConnectPc(pc) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    },
                    tonalElevation = if (isSelected) 3.dp else 1.dp
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isSelected) SpeakerIcons.CheckCircle else SpeakerIcons.Computer,
                            contentDescription = null,
                            tint = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = pc.hostName,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            Text(
                                text = pc.ip,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        IconButton(
                            onClick = { onDeletePc(pc) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = SpeakerIcons.Close,
                                contentDescription = "Remove saved PC",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualityPresetSection(
    currentMode: LatencyMode,
    onSelectMode: (LatencyMode) -> Unit,
    onOpenTuningSheet: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Audio Quality & Sync",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = onOpenTuningSheet) {
                Icon(
                    imageVector = SpeakerIcons.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Tuning")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val presets = listOf(
                LatencyMode.LOW_LATENCY to "Low Latency",
                LatencyMode.BALANCE to "Balanced",
                LatencyMode.HIGH_QUALITY to "High Quality",
                LatencyMode.BEST_QUALITY to "Best Quality",
                LatencyMode.CUSTOM_SETTING to "Custom"
            )
            presets.forEach { (mode, label) ->
                val selected = currentMode == mode
                FilterChip(
                    selected = selected,
                    onClick = {
                        onSelectMode(mode)
                        if (mode == LatencyMode.CUSTOM_SETTING) {
                            onOpenTuningSheet()
                        }
                    },
                    label = {
                        Text(
                            text = label,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun NumberMatchDialog(
    challenge: PairChallenge,
    isBusy: Boolean,
    onSelectChoice: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        icon = {
            Icon(
                imageVector = SpeakerIcons.Pin,
                contentDescription = null
            )
        },
        title = {
            Text(
                text = "Match number on ${challenge.hostName}",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Tap the 2-digit number shown in Mobile Speaker on your PC (${challenge.serverIp}):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    challenge.choices.forEach { num ->
                        FilledTonalButton(
                            enabled = !isBusy,
                            onClick = { onSelectChoice(num) },
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
            TextButton(enabled = !isBusy, onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTuningBottomSheet(
    initialBitrate: Int,
    initialComplexity: Int,
    initialBufferSize: Int,
    initialPcmTarget: Int,
    initialCatchup: Int,
    initialPlcEnabled: Boolean,
    onApply: (bitrate: Int, complexity: Int, bufferSize: Int, pcmTarget: Int, catchup: Int, plcEnabled: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var advBitrate by remember { mutableStateOf(initialBitrate) }
    var advComplexity by remember { mutableStateOf(initialComplexity.toFloat()) }
    var advBufferSize by remember { mutableStateOf(initialBufferSize.toFloat()) }
    var advPcmTarget by remember { mutableStateOf(initialPcmTarget.toFloat()) }
    var advCatchup by remember { mutableStateOf(initialCatchup.toFloat()) }
    var plcEnabled by remember { mutableStateOf(initialPlcEnabled) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "Custom Audio Tuning",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Packet Loss Concealment (PLC)", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Smooths brief Wi-Fi dropouts automatically",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = plcEnabled, onCheckedChange = { plcEnabled = it })
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Text("Bitrate: ${advBitrate / 1000} kbps", fontWeight = FontWeight.SemiBold)
            val bitrates = listOf(48000, 96000, 128000, 160000, 192000, 256000, 320000)
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                bitrates.forEach { br ->
                    FilterChip(
                        selected = advBitrate == br,
                        onClick = { advBitrate = br },
                        label = { Text("${br / 1000}k") },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Encoder Complexity: ${advComplexity.toInt()}", fontWeight = FontWeight.SemiBold)
            Slider(value = advComplexity, onValueChange = { advComplexity = it }, valueRange = 0f..10f, steps = 9)

            Text("Buffer Target: ${advBufferSize.toInt()} frames (${advBufferSize.toInt() * 20} ms)", fontWeight = FontWeight.SemiBold)
            Slider(
                value = advBufferSize,
                onValueChange = {
                    advBufferSize = it
                    if (advPcmTarget > advBufferSize) advPcmTarget = advBufferSize
                },
                valueRange = 1f..20f,
                steps = 18
            )

            Text("PCM Pre-decode Target: ${advPcmTarget.toInt()}", fontWeight = FontWeight.SemiBold)
            Slider(
                value = advPcmTarget,
                onValueChange = { advPcmTarget = it },
                valueRange = 1f..advBufferSize.coerceAtLeast(1f),
                steps = (advBufferSize.toInt() - 2).coerceAtLeast(0)
            )

            Text("Catch-up Threshold: +${advCatchup.toInt()}", fontWeight = FontWeight.SemiBold)
            Slider(value = advCatchup, onValueChange = { advCatchup = it }, valueRange = 1f..10f, steps = 8)

            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = {
                    onApply(
                        advBitrate,
                        advComplexity.toInt(),
                        advBufferSize.toInt(),
                        advPcmTarget.toInt(),
                        advCatchup.toInt(),
                        plcEnabled
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp)
            ) {
                Text("Apply Custom Tuning", fontWeight = FontWeight.Bold)
            }
        }
    }
}
