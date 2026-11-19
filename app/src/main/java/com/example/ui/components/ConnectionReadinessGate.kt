package com.example.ui.components

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.transfer.LocalNetworkUtils
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.MintSuccess

class RadioReadinessController(
    private val launchPermissions: () -> Unit,
    private val launchBluetoothEnable: () -> Unit,
    private val launchWifiPanel: () -> Unit,
    private val startChain: () -> Unit
) {
    fun requestPermissionsOnly() = launchPermissions()
    fun requestBluetoothOnly() = launchBluetoothEnable()
    fun requestWifiOnly() = launchWifiPanel()
    fun enableAllInSequence() = startChain()
}

@Composable
fun rememberRadioReadinessController(
    bluetoothSupported: Boolean,
    onPermissionResult: (Boolean) -> Unit,
    onRefreshRadioStates: () -> Unit,
    onAllReady: () -> Unit = {}
): RadioReadinessController {
    val context = LocalContext.current
    var chainActive by remember { mutableStateOf(false) }

    val wifiPanelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onRefreshRadioStates()
        chainActive = false
        val ready = LocalNetworkUtils.hasDiscoveryPermission(context) &&
            LocalNetworkUtils.isWifiEnabled(context) &&
            (!bluetoothSupported || LocalNetworkUtils.isBluetoothEnabled(context))
        if (ready) {
            onAllReady()
        }
    }

    val bluetoothEnableLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onRefreshRadioStates()
        if (chainActive) {
            if (!LocalNetworkUtils.isWifiEnabled(context)) {
                wifiPanelLauncher.launch(LocalNetworkUtils.createEnableWifiIntent())
            } else {
                chainActive = false
                if (LocalNetworkUtils.hasDiscoveryPermission(context) &&
                    (!bluetoothSupported || LocalNetworkUtils.isBluetoothEnabled(context))
                ) {
                    onAllReady()
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it } || LocalNetworkUtils.hasDiscoveryPermission(context)
        onPermissionResult(granted)
        onRefreshRadioStates()
        if (chainActive && granted) {
            when {
                bluetoothSupported && !LocalNetworkUtils.isBluetoothEnabled(context) -> {
                    bluetoothEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }
                !LocalNetworkUtils.isWifiEnabled(context) -> {
                    wifiPanelLauncher.launch(LocalNetworkUtils.createEnableWifiIntent())
                }
                else -> {
                    chainActive = false
                    onAllReady()
                }
            }
        } else {
            chainActive = false
        }
    }

    return remember(bluetoothSupported) {
        RadioReadinessController(
            launchPermissions = {
                chainActive = false
                permissionLauncher.launch(LocalNetworkUtils.requiredDiscoveryPermissions())
            },
            launchBluetoothEnable = {
                chainActive = false
                if (!LocalNetworkUtils.hasBluetoothConnectPermission(context)) {
                    permissionLauncher.launch(LocalNetworkUtils.requiredDiscoveryPermissions())
                } else {
                    bluetoothEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }
            },
            launchWifiPanel = {
                chainActive = false
                wifiPanelLauncher.launch(LocalNetworkUtils.createEnableWifiIntent())
            },
            startChain = {
                onRefreshRadioStates()
                val hasPerms = LocalNetworkUtils.hasDiscoveryPermission(context)
                val btOn = !bluetoothSupported || LocalNetworkUtils.isBluetoothEnabled(context)
                val wifiOn = LocalNetworkUtils.isWifiEnabled(context)

                when {
                    !hasPerms -> {
                        chainActive = true
                        permissionLauncher.launch(LocalNetworkUtils.requiredDiscoveryPermissions())
                    }
                    !btOn -> {
                        chainActive = true
                        bluetoothEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    }
                    !wifiOn -> {
                        chainActive = true
                        wifiPanelLauncher.launch(LocalNetworkUtils.createEnableWifiIntent())
                    }
                    else -> {
                        chainActive = false
                        onAllReady()
                    }
                }
            }
        )
    }
}

@Composable
fun ConnectionReadinessCard(
    hasDiscoveryPermission: Boolean,
    wifiEnabled: Boolean,
    bluetoothSupported: Boolean,
    bluetoothEnabled: Boolean,
    onRequestPermissions: () -> Unit,
    onEnableWifi: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onEnableAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(CobaltPrimary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WifiTethering,
                        contentDescription = null,
                        tint = CobaltPrimary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.readiness_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = stringResource(R.string.readiness_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }

            ReadinessChecklist(
                hasDiscoveryPermission = hasDiscoveryPermission,
                wifiEnabled = wifiEnabled,
                bluetoothSupported = bluetoothSupported,
                bluetoothEnabled = bluetoothEnabled,
                onRequestPermissions = onRequestPermissions,
                onEnableWifi = onEnableWifi,
                onEnableBluetooth = onEnableBluetooth
            )

            Button(
                onClick = onEnableAll,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("readiness_enable_all_button")
            ) {
                Text(stringResource(R.string.readiness_enable_button))
            }
        }
    }
}

@Composable
fun ConnectionReadinessDialog(
    hasDiscoveryPermission: Boolean,
    wifiEnabled: Boolean,
    bluetoothSupported: Boolean,
    bluetoothEnabled: Boolean,
    onRequestPermissions: () -> Unit,
    onEnableWifi: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onEnableAll: () -> Unit,
    onDismiss: () -> Unit,
    onAllReady: () -> Unit
) {
    val allReady = hasDiscoveryPermission &&
        wifiEnabled &&
        (!bluetoothSupported || bluetoothEnabled)

    LaunchedEffect(allReady) {
        if (allReady) {
            onAllReady()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.WifiTethering,
                contentDescription = null,
                tint = CobaltPrimary
            )
        },
        title = {
            Text(
                text = stringResource(R.string.readiness_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.readiness_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ReadinessChecklist(
                    hasDiscoveryPermission = hasDiscoveryPermission,
                    wifiEnabled = wifiEnabled,
                    bluetoothSupported = bluetoothSupported,
                    bluetoothEnabled = bluetoothEnabled,
                    onRequestPermissions = onRequestPermissions,
                    onEnableWifi = onEnableWifi,
                    onEnableBluetooth = onEnableBluetooth
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onEnableAll,
                modifier = Modifier.testTag("dialog_enable_radios_button")
            ) {
                Text("Allow & Turn On")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dialog_dismiss_radios_button")
            ) {
                Text("Not Now")
            }
        }
    )
}

@Composable
private fun ReadinessChecklist(
    hasDiscoveryPermission: Boolean,
    wifiEnabled: Boolean,
    bluetoothSupported: Boolean,
    bluetoothEnabled: Boolean,
    onRequestPermissions: () -> Unit,
    onEnableWifi: () -> Unit,
    onEnableBluetooth: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReadinessChecklistRow(
            icon = Icons.Rounded.Security,
            title = "Nearby Devices Permission",
            subtitle = if (hasDiscoveryPermission) "Allowed" else "Tap to allow Wi-Fi & Bluetooth access",
            ready = hasDiscoveryPermission,
            onClick = onRequestPermissions,
            testTag = "readiness_row_permission"
        )
        ReadinessChecklistRow(
            icon = Icons.Rounded.Wifi,
            title = "Wi-Fi",
            subtitle = if (wifiEnabled) "Turned ON" else "OFF — Tap to turn on Wi-Fi",
            ready = wifiEnabled,
            onClick = onEnableWifi,
            testTag = "readiness_row_wifi"
        )
        if (bluetoothSupported) {
            ReadinessChecklistRow(
                icon = Icons.Rounded.Bluetooth,
                title = "Bluetooth",
                subtitle = if (bluetoothEnabled) "Turned ON" else "OFF — Tap to turn on Bluetooth",
                ready = bluetoothEnabled,
                onClick = onEnableBluetooth,
                testTag = "readiness_row_bluetooth"
            )
        }
    }
}

@Composable
private fun ReadinessChecklistRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    ready: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val accent = if (ready) MintSuccess else AmberWarning
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = !ready, onClick = onClick)
            .minimumInteractiveComponentSize()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ready) MintSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (ready) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = if (ready) "$title ready" else "$title action needed",
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
