package com.example.ui.screens

import android.bluetooth.BluetoothAdapter
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.R
import com.example.data.local.DevicePreferences
import com.example.transfer.FileSaver
import com.example.transfer.IncomingTransferOffer
import com.example.transfer.LocalNetworkUtils
import com.example.transfer.QrPairingCodec
import com.example.transfer.ReceiverEndpointInfo
import com.example.transfer.TransferSessionState
import com.example.ui.components.ConnectionReadinessCard
import com.example.ui.components.ConnectionReadinessDialog
import com.example.ui.components.IncomingTransferDialog
import com.example.ui.components.RadarPulseCanvas
import com.example.ui.components.TransferProgressCard
import com.example.ui.components.rememberRadioReadinessController
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MintSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReceiveScreen(
    devicePreferences: DevicePreferences,
    endpointInfo: ReceiverEndpointInfo,
    incomingOffer: IncomingTransferOffer?,
    wifiEnabled: Boolean,
    bluetoothSupported: Boolean,
    bluetoothEnabled: Boolean,
    sessionState: TransferSessionState,
    onStartReceiveMode: () -> Unit,
    onStopReceiveMode: () -> Unit,
    onRefreshRadioStates: () -> Unit,
    onRespondOffer: (Boolean) -> Unit,
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
        onStartReceiveMode()
        onPauseOrDispose {}
    }
    val isReadyToConnect = hasDiscoveryPermission &&
        wifiEnabled &&
        (!bluetoothSupported || bluetoothEnabled)

    var showReadinessDialog by remember { mutableStateOf(!isReadyToConnect) }

    val readinessController = rememberRadioReadinessController(
        bluetoothSupported = bluetoothSupported,
        onPermissionResult = { granted ->
            hasDiscoveryPermission = granted
            onStartReceiveMode()
        },
        onRefreshRadioStates = onRefreshRadioStates,
        onAllReady = {
            showReadinessDialog = false
            onStartReceiveMode()
        }
    )

    val btActionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onRefreshRadioStates()
        onStartReceiveMode()
    }

    LaunchedEffect(wifiEnabled, bluetoothEnabled, hasDiscoveryPermission) {
        onStartReceiveMode()
        if (wifiEnabled) {
            delay(1200)
            onStartReceiveMode()
        }
    }

    DisposableEffect(Unit) {
        onRefreshRadioStates()
        onStartReceiveMode()
        onDispose {
            onStopReceiveMode()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column {
                    Text(
                        text = stringResource(R.string.receive_title),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = stringResource(R.string.receive_waiting),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

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

            // Receiver Radar Beacon
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        RadarPulseCanvas(
                            isActive = endpointInfo.isListening || endpointInfo.bluetoothListening,
                            accentColor = CyanSecondary,
                            deviceName = devicePreferences.deviceName,
                            avatarColorIndex = devicePreferences.avatarColorIndex,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatusPill(
                                icon = Icons.Rounded.Wifi,
                                label = if (endpointInfo.isListening) "Wi-Fi LAN Active" else "Wi-Fi Starting...",
                                active = endpointInfo.isListening
                            )
                            StatusPill(
                                icon = Icons.Rounded.WifiTethering,
                                label = if (endpointInfo.wifiDirectReady) "Wi-Fi Direct Ready" else "Direct Standby",
                                active = endpointInfo.wifiDirectReady
                            )
                            if (bluetoothSupported) {
                                StatusPill(
                                    icon = Icons.Rounded.Bluetooth,
                                    label = if (endpointInfo.bluetoothListening) "Bluetooth Active" else "Bluetooth Off",
                                    active = endpointInfo.bluetoothListening
                                )
                            }
                        }

                        if (bluetoothSupported) {
                            FilledTonalButton(
                                onClick = {
                                    val intent = if (!endpointInfo.bluetoothEnabled) {
                                        Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                    } else {
                                        Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                                            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
                                        }
                                    }
                                    btActionLauncher.launch(intent)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("bluetooth_discoverable_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.BluetoothSearching,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (!endpointInfo.bluetoothEnabled) {
                                        "Turn On Bluetooth Receiver"
                                    } else {
                                        "Make Bluetooth Discoverable (120s)"
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Scan-to-Connect QR Code Card
            item {
                val qrEndpoint = remember(endpointInfo, devicePreferences.deviceName) {
                    endpointInfo.copy(deviceName = devicePreferences.deviceName)
                }
                val qrPayload = remember(qrEndpoint) {
                    QrPairingCodec.encode(qrEndpoint)
                }
                val qrBitmap by produceState<Bitmap?>(initialValue = null, key1 = qrPayload) {
                    value = withContext(Dispatchers.Default) {
                        QrPairingCodec.generateQrBitmap(qrPayload, 512)
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.QrCode2,
                                contentDescription = null,
                                tint = CyanSecondary
                            )
                            Text(
                                text = stringResource(R.string.receive_qr_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Text(
                            text = stringResource(R.string.receive_qr_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White,
                            tonalElevation = 2.dp
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(216.dp)
                                    .padding(14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val bmp = qrBitmap
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "QR pairing code for ${devicePreferences.deviceName}",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .testTag("receive_qr_image")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Direct Address & Session PIN Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Direct Connection Details",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Nearby senders discover you automatically over Wi-Fi and Bluetooth. For manual Wi-Fi connection, share this address and PIN:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                modifier = Modifier.weight(1.4f),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.receive_address_label),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${endpointInfo.localIp}:${endpointInfo.port}",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(
                                                ClipData.newPlainText(
                                                    "ShareMe Address",
                                                    "${endpointInfo.localIp}:${endpointInfo.port}"
                                                )
                                            )
                                            Toast.makeText(context, "Address copied", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.testTag("copy_receiver_address_button")
                                    ) {
                                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy address")
                                    }
                                }
                            }

                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.Lock,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.receive_pin_label),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                    Text(
                                        text = endpointInfo.sessionPin,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }

    if (showReadinessDialog && !isReadyToConnect) {
        ConnectionReadinessDialog(
            hasDiscoveryPermission = hasDiscoveryPermission,
            wifiEnabled = wifiEnabled,
            bluetoothSupported = bluetoothSupported,
            bluetoothEnabled = bluetoothEnabled,
            onRequestPermissions = readinessController::requestPermissionsOnly,
            onEnableWifi = readinessController::requestWifiOnly,
            onEnableBluetooth = readinessController::requestBluetoothOnly,
            onEnableAll = readinessController::enableAllInSequence,
            onDismiss = { showReadinessDialog = false },
            onAllReady = {
                showReadinessDialog = false
                onStartReceiveMode()
            }
        )
    }

    if (incomingOffer != null) {
        IncomingTransferDialog(
            offer = incomingOffer,
            onAccept = { onRespondOffer(true) },
            onDecline = { onRespondOffer(false) }
        )
    }
}

@Composable
private fun StatusPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean
) {
    val tint = if (active) MintSuccess else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(99.dp),
        color = tint.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = tint
            )
        }
    }
}
