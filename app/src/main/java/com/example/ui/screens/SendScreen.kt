package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.DevicePreferences
import com.example.transfer.DEFAULT_TRANSFER_PORT
import com.example.transfer.FileCategory
import com.example.transfer.FileSaver
import com.example.transfer.LocalNetworkUtils
import com.example.transfer.NearbyPeer
import com.example.transfer.PeerTransport
import com.example.transfer.QrPairingPayload
import com.example.transfer.ShareableFile
import com.example.transfer.TransferSessionState
import com.example.ui.components.ConnectionReadinessCard
import com.example.ui.components.ConnectionReadinessDialog
import com.example.ui.components.FileCategoryBadge
import com.example.ui.components.QrScannerDialog
import com.example.ui.components.RadarPulseCanvas
import com.example.ui.components.TransferProgressCard
import com.example.ui.components.rememberRadioReadinessController
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MintSuccess
import com.example.ui.theme.VioletTertiary

private enum class PendingSendAction {
    SCAN_QR,
    PEER_TAP,
    MANUAL_IP
}

@Composable
fun SendScreen(
    devicePreferences: DevicePreferences,
    queuedFiles: List<ShareableFile>,
    discoveredPeers: List<NearbyPeer>,
    pairedPeer: NearbyPeer?,
    isScanning: Boolean,
    discoveryStatus: String?,
    wifiEnabled: Boolean,
    bluetoothSupported: Boolean,
    bluetoothEnabled: Boolean,
    sessionState: TransferSessionState,
    onAddPickedUris: (List<Uri>, Boolean) -> Unit,
    onRemoveFile: (String) -> Unit,
    onClearQueue: () -> Unit,
    onClearPairedPeer: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onRefreshRadioStates: () -> Unit,
    onPeerClicked: (NearbyPeer, () -> Unit) -> Unit,
    onSendFromQr: (QrPairingPayload, () -> Unit) -> Unit,
    onSendDirectToIp: (String, Int, String) -> Unit,
    onCancelTransfer: () -> Unit,
    onResetTransferState: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasDiscoveryPermission by remember {
        mutableStateOf(LocalNetworkUtils.hasDiscoveryPermission(context))
    }

    LifecycleResumeEffect(Unit) {
        hasDiscoveryPermission = LocalNetworkUtils.hasDiscoveryPermission(context)
        onRefreshRadioStates()
        onPauseOrDispose {}
    }
    var showDirectConnectDialog by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    var showReadinessDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<PendingSendAction?>(null) }
    var pendingPeer by remember { mutableStateOf<NearbyPeer?>(null) }

    val isReadyToConnect = hasDiscoveryPermission &&
        wifiEnabled &&
        (!bluetoothSupported || bluetoothEnabled)

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showQrScanner = true
        } else {
            Toast.makeText(
                context,
                "Camera permission is required to scan a receiver QR code",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val launchQrScannerWithCameraCheck: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            showQrScanner = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50)
    ) { uris ->
        onAddPickedUris(uris, true)
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        onAddPickedUris(uris, true)
    }

    val executePendingAction: () -> Unit = {
        val action = pendingAction
        val peer = pendingPeer
        pendingAction = null
        pendingPeer = null
        when (action) {
            PendingSendAction.SCAN_QR -> launchQrScannerWithCameraCheck()
            PendingSendAction.PEER_TAP -> if (peer != null) {
                onPeerClicked(peer) { filePicker.launch(arrayOf("*/*")) }
            }
            PendingSendAction.MANUAL_IP -> showDirectConnectDialog = true
            null -> Unit
        }
    }

    val readinessController = rememberRadioReadinessController(
        bluetoothSupported = bluetoothSupported,
        onPermissionResult = { granted ->
            hasDiscoveryPermission = granted
            if (granted) {
                onStartScan()
            }
        },
        onRefreshRadioStates = onRefreshRadioStates,
        onAllReady = {
            onStartScan()
            if (showReadinessDialog) {
                showReadinessDialog = false
                executePendingAction()
            }
        }
    )

    DisposableEffect(hasDiscoveryPermission, wifiEnabled, bluetoothEnabled) {
        onRefreshRadioStates()
        onStartScan()
        onDispose {
            onStopScan()
        }
    }

    val totalQueuedBytes = remember(queuedFiles) { queuedFiles.sumOf { it.sizeBytes } }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .testTag("send_lazy_column"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.send_title),
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = if (queuedFiles.isEmpty()) {
                                "Pick files to share nearby"
                            } else {
                                "${queuedFiles.size} file(s) • ${LocalNetworkUtils.formatBytes(totalQueuedBytes)}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (queuedFiles.isNotEmpty()) {
                        TextButton(
                            onClick = onClearQueue,
                            modifier = Modifier.testTag("clear_queue_button")
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear")
                        }
                    }
                }
            }

            // Active Transfer Progress Card
            if (sessionState !is TransferSessionState.Idle) {
                item {
                    TransferProgressCard(
                        sessionState = sessionState,
                        onCancel = onCancelTransfer,
                        onDismiss = onResetTransferState,
                        onOpenFile = { uri -> FileSaver.openFile(context, uri, "*/*") },
                        onShareFile = { uri -> FileSaver.shareFileViaChooser(context, uri, "*/*") }
                    )
                }
            }

            // Picker Action Buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            mediaPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_media_button")
                    ) {
                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Photos", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    FilledTonalButton(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_files_button")
                    ) {
                        Icon(Icons.Rounded.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Files", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    FilledTonalButton(
                        onClick = { filePicker.launch(arrayOf("audio/*")) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_audio_button")
                    ) {
                        Icon(Icons.Rounded.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Audio", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // Connected / Paired Receiver Banner (when connected via QR or radar)
            if (pairedPeer != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MintSuccess.copy(alpha = 0.14f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MintSuccess
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Connected: ${pairedPeer.name}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (queuedFiles.isEmpty()) {
                                        "Tap Ready to Share below to pick files & send automatically"
                                    } else {
                                        "${pairedPeer.statusLabel} • Ready to transfer"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = onClearPairedPeer,
                                modifier = Modifier.testTag("clear_paired_peer_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Disconnect paired phone"
                                )
                            }
                        }
                    }
                }
            }

            // Queued Files Section
            if (queuedFiles.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .clickable { filePicker.launch(arrayOf("*/*")) }
                            .testTag("ready_to_share_card"),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Select files to share",
                                tint = CobaltPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "Ready to Share",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.ready_to_share_cta),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Surface(
                                shape = RoundedCornerShape(99.dp),
                                color = CobaltPrimary.copy(alpha = 0.12f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FolderOpen,
                                        contentDescription = null,
                                        tint = CobaltPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Select Files & Data",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = CobaltPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                items(queuedFiles, key = { it.id }) { file ->
                    QueuedFileItemRow(
                        file = file,
                        onRemove = { onRemoveFile(file.id) }
                    )
                }
            }

            // Wi-Fi + Bluetooth + Permission Readiness Gate
            if (!isReadyToConnect) {
                item {
                    ConnectionReadinessCard(
                        hasDiscoveryPermission = hasDiscoveryPermission,
                        wifiEnabled = wifiEnabled,
                        bluetoothSupported = bluetoothSupported,
                        bluetoothEnabled = bluetoothEnabled,
                        onRequestPermissions = readinessController::requestPermissionsOnly,
                        onEnableWifi = readinessController::requestWifiOnly,
                        onEnableBluetooth = readinessController::requestBluetoothOnly,
                        onEnableAll = readinessController::enableAllInSequence
                    )
                }
            }

            // Nearby Devices Radar & Pairing Card (Always visible so users can search, tap radar phones, or scan QR anytime)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Radar,
                                    contentDescription = null,
                                    tint = CobaltPrimary
                                )
                                Text(
                                    text = "Nearby Receivers",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            IconButton(
                                onClick = onStartScan,
                                modifier = Modifier.testTag("rescan_peers_button")
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Rescan devices")
                            }
                        }

                        RadarPulseCanvas(
                            isActive = isScanning,
                            accentColor = CobaltPrimary,
                            deviceName = devicePreferences.deviceName,
                            avatarColorIndex = devicePreferences.avatarColorIndex,
                            peers = discoveredPeers,
                            selectedPeerId = pairedPeer?.id,
                            onPeerClick = { peer ->
                                onRefreshRadioStates()
                                if (!isReadyToConnect) {
                                    pendingPeer = peer
                                    pendingAction = PendingSendAction.PEER_TAP
                                    showReadinessDialog = true
                                } else {
                                    onPeerClicked(peer) {
                                        filePicker.launch(arrayOf("*/*"))
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(228.dp)
                        )

                        if (!discoveryStatus.isNullOrBlank()) {
                            Text(
                                text = discoveryStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }

                        Button(
                            onClick = {
                                onRefreshRadioStates()
                                if (!isReadyToConnect) {
                                    pendingAction = PendingSendAction.SCAN_QR
                                    showReadinessDialog = true
                                } else {
                                    launchQrScannerWithCameraCheck()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("scan_qr_button")
                        ) {
                            Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.send_scan_qr))
                        }

                        OutlinedButton(
                            onClick = {
                                onRefreshRadioStates()
                                if (!isReadyToConnect) {
                                    pendingAction = PendingSendAction.MANUAL_IP
                                    showReadinessDialog = true
                                } else {
                                    showDirectConnectDialog = true
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("manual_ip_connect_button")
                        ) {
                            Icon(Icons.Rounded.Dialpad, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.send_manual_connect))
                        }
                    }
                }
            }

            // Discovered Peers List
            if (discoveredPeers.isNotEmpty()) {
                items(discoveredPeers, key = { it.id }) { peer ->
                    DiscoveredPeerCard(
                        peer = peer,
                        onClick = {
                            onRefreshRadioStates()
                            if (!isReadyToConnect) {
                                pendingPeer = peer
                                pendingAction = PendingSendAction.PEER_TAP
                                showReadinessDialog = true
                            } else {
                                onPeerClicked(peer) {
                                    filePicker.launch(arrayOf("*/*"))
                                }
                            }
                        }
                    )
                }
            } else {
                item {
                    Text(
                        text = "Ask the receiving phone to open ShareMe -> Receive, then tap its phone on the radar or tap \"Scan QR Code\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }

    if (showReadinessDialog) {
        ConnectionReadinessDialog(
            hasDiscoveryPermission = hasDiscoveryPermission,
            wifiEnabled = wifiEnabled,
            bluetoothSupported = bluetoothSupported,
            bluetoothEnabled = bluetoothEnabled,
            onRequestPermissions = readinessController::requestPermissionsOnly,
            onEnableWifi = readinessController::requestWifiOnly,
            onEnableBluetooth = readinessController::requestBluetoothOnly,
            onEnableAll = readinessController::enableAllInSequence,
            onDismiss = {
                showReadinessDialog = false
                pendingAction = null
                pendingPeer = null
            },
            onAllReady = {
                showReadinessDialog = false
                executePendingAction()
            }
        )
    }

    if (showQrScanner) {
        QrScannerDialog(
            onDismiss = { showQrScanner = false },
            onQrScanned = { payload ->
                showQrScanner = false
                onSendFromQr(payload) {
                    Toast.makeText(
                        context,
                        "Connected to ${payload.deviceName.ifBlank { "Receiver" }} — select files to send",
                        Toast.LENGTH_SHORT
                    ).show()
                    filePicker.launch(arrayOf("*/*"))
                }
            },
            onManualConnectFallback = {
                showQrScanner = false
                showDirectConnectDialog = true
            }
        )
    }

    if (showDirectConnectDialog) {
        ManualIpConnectDialog(
            onDismiss = { showDirectConnectDialog = false },
            onConnect = { ip, port, pin ->
                showDirectConnectDialog = false
                onSendDirectToIp(ip, port, pin)
            }
        )
    }
}

@Composable
private fun QueuedFileItemRow(
    file: ShareableFile,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (file.category == FileCategory.PHOTO || file.category == FileCategory.VIDEO) {
                AsyncImage(
                    model = file.uri,
                    contentDescription = file.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                FileCategoryBadge(category = file.category)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = LocalNetworkUtils.formatBytes(file.sizeBytes),
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier.testTag("remove_queued_file_${file.name}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Remove ${file.name}"
                )
            }
        }
    }
}

@Composable
private fun DiscoveredPeerCard(
    peer: NearbyPeer,
    onClick: () -> Unit
) {
    val (icon, tint) = when (peer.transport) {
        PeerTransport.WIFI_DIRECT -> Icons.Rounded.PhoneAndroid to CobaltPrimary
        PeerTransport.LOCAL_WIFI -> Icons.Rounded.Wifi to CyanSecondary
        PeerTransport.BLUETOOTH -> Icons.Rounded.Bluetooth to VioletTertiary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("peer_card_${peer.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = tint.copy(alpha = 0.14f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = peer.transport.name,
                    tint = tint,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = peer.statusLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(99.dp),
                color = tint
            ) {
                Text(
                    text = "Send",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun ManualIpConnectDialog(
    onDismiss: () -> Unit,
    onConnect: (String, Int, String) -> Unit
) {
    var ipText by remember { mutableStateOf("192.168.") }
    var portText by remember { mutableStateOf(DEFAULT_TRANSFER_PORT.toString()) }
    var pinText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Direct Receiver Connect") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter the IP address and 4-digit Session PIN shown on the receiving phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = ipText,
                    onValueChange = { ipText = it },
                    label = { Text("Receiver IP Address") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_ip_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it.filter { c -> c.isDigit() }.take(5) },
                        label = { Text("Port") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("manual_port_input")
                    )
                    OutlinedTextField(
                        value = pinText,
                        onValueChange = { pinText = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("PIN (Optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("manual_pin_input")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val port = portText.toIntOrNull() ?: DEFAULT_TRANSFER_PORT
                    onConnect(ipText, port, pinText)
                },
                enabled = ipText.trim().length >= 7,
                modifier = Modifier.testTag("manual_connect_confirm_button")
            ) {
                Text("Connect & Send")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
