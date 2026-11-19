package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ReceiveScreen
import com.example.ui.screens.SendScreen
import com.example.ui.screens.SettingsScreen

object ShareMeRoutes {
    const val HOME = "home"
    const val SEND = "send"
    const val RECEIVE = "receive"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}

private data class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val TopLevelDestinations = listOf(
    TopLevelDestination(
        route = ShareMeRoutes.HOME,
        labelRes = R.string.nav_home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_home"
    ),
    TopLevelDestination(
        route = ShareMeRoutes.SEND,
        labelRes = R.string.nav_send,
        selectedIcon = Icons.Filled.Upload,
        unselectedIcon = Icons.Outlined.Upload,
        testTag = "nav_send"
    ),
    TopLevelDestination(
        route = ShareMeRoutes.RECEIVE,
        labelRes = R.string.nav_receive,
        selectedIcon = Icons.Filled.Download,
        unselectedIcon = Icons.Outlined.Download,
        testTag = "nav_receive"
    ),
    TopLevelDestination(
        route = ShareMeRoutes.HISTORY,
        labelRes = R.string.nav_history,
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History,
        testTag = "nav_history"
    )
)

@Composable
fun ShareMeApp(
    viewModel: ShareMeViewModel,
    isDarkTheme: Boolean = false
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ShareMeRoutes.HOME

    val devicePreferences by viewModel.devicePreferences.collectAsStateWithLifecycle()
    val queuedFiles by viewModel.queuedFiles.collectAsStateWithLifecycle()
    val pairedPeer by viewModel.pairedPeer.collectAsStateWithLifecycle()
    val discoveredPeers by viewModel.discoveredPeers.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val discoveryStatus by viewModel.discoveryStatus.collectAsStateWithLifecycle()
    val wifiEnabled by viewModel.wifiEnabled.collectAsStateWithLifecycle()
    val bluetoothEnabled by viewModel.bluetoothEnabled.collectAsStateWithLifecycle()
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val receiverEndpoint by viewModel.receiverEndpoint.collectAsStateWithLifecycle()
    val incomingOffer by viewModel.incomingOffer.collectAsStateWithLifecycle()
    val allTransfers by viewModel.allTransfers.collectAsStateWithLifecycle()
    val recentTransfers by viewModel.recentTransfers.collectAsStateWithLifecycle()

    val navigateToTopLevel: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val showChromeNav = currentRoute != ShareMeRoutes.SETTINGS

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val useNavigationRail = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useNavigationRail && showChromeNav) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    TopLevelDestinations.forEach { dest ->
                        val selected = currentRoute == dest.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateToTopLevel(dest.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = stringResource(dest.labelRes)
                                )
                            },
                            label = { Text(stringResource(dest.labelRes)) },
                            modifier = Modifier.testTag(dest.testTag)
                        )
                    }
                }
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                contentWindowInsets = WindowInsets.safeDrawing,
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (!useNavigationRail && showChromeNav) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.navigationBarsPadding()
                        ) {
                            TopLevelDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateToTopLevel(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(dest.labelRes)) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = ShareMeRoutes.HOME
                    ) {
                        composable(ShareMeRoutes.HOME) {
                            HomeScreen(
                                devicePreferences = devicePreferences,
                                recentTransfers = recentTransfers,
                                isDarkTheme = isDarkTheme,
                                onToggleTheme = { viewModel.toggleDayNightTheme(isDarkTheme) },
                                onNavigateToSend = { navigateToTopLevel(ShareMeRoutes.SEND) },
                                onNavigateToReceive = { navigateToTopLevel(ShareMeRoutes.RECEIVE) },
                                onNavigateToHistory = { navigateToTopLevel(ShareMeRoutes.HISTORY) },
                                onNavigateToSettings = { navController.navigate(ShareMeRoutes.SETTINGS) },
                                onQuickPickedFiles = { uris -> viewModel.addPickedUris(context, uris) }
                            )
                        }
                        composable(ShareMeRoutes.SEND) {
                            SendScreen(
                                devicePreferences = devicePreferences,
                                queuedFiles = queuedFiles,
                                discoveredPeers = discoveredPeers,
                                pairedPeer = pairedPeer,
                                isScanning = isScanning,
                                discoveryStatus = discoveryStatus,
                                wifiEnabled = wifiEnabled,
                                bluetoothSupported = viewModel.bluetoothSupported,
                                bluetoothEnabled = bluetoothEnabled,
                                sessionState = sessionState,
                                onAddPickedUris = { uris, autoSend ->
                                    viewModel.addPickedUris(context, uris, autoSendIfPaired = autoSend)
                                },
                                onRemoveFile = viewModel::removeQueuedFile,
                                onClearQueue = viewModel::clearQueue,
                                onClearPairedPeer = viewModel::clearPairedPeer,
                                onStartScan = viewModel::startNearbyScan,
                                onStopScan = viewModel::stopNearbyScan,
                                onRefreshRadioStates = viewModel::refreshRadioStates,
                                onPeerClicked = viewModel::onPeerClicked,
                                onSendFromQr = viewModel::connectAndSendFromQr,
                                onSendDirectToIp = viewModel::sendDirectToIp,
                                onCancelTransfer = viewModel::cancelTransfer,
                                onResetTransferState = viewModel::resetTransferState
                            )
                        }
                        composable(ShareMeRoutes.RECEIVE) {
                            ReceiveScreen(
                                devicePreferences = devicePreferences,
                                endpointInfo = receiverEndpoint,
                                incomingOffer = incomingOffer,
                                wifiEnabled = wifiEnabled,
                                bluetoothSupported = viewModel.bluetoothSupported,
                                bluetoothEnabled = bluetoothEnabled,
                                sessionState = sessionState,
                                onStartReceiveMode = { viewModel.startReceiveMode(context) },
                                onStopReceiveMode = viewModel::stopReceiveMode,
                                onRefreshRadioStates = viewModel::refreshRadioStates,
                                onRespondOffer = viewModel::respondToIncomingOffer,
                                onCancelTransfer = viewModel::cancelTransfer,
                                onResetTransferState = viewModel::resetTransferState
                            )
                        }
                        composable(ShareMeRoutes.HISTORY) {
                            HistoryScreen(
                                transfers = allTransfers,
                                onDeleteRecord = viewModel::deleteHistoryRecord,
                                onClearAll = viewModel::clearHistory
                            )
                        }
                        composable(ShareMeRoutes.SETTINGS) {
                            SettingsScreen(
                                devicePreferences = devicePreferences,
                                onUpdateDeviceName = viewModel::updateDeviceName,
                                onUpdateAvatarColor = viewModel::updateAvatarColor,
                                onSetAutoAccept = viewModel::setAutoAcceptIncoming,
                                onSetPreferWifiDirect = viewModel::setPreferWifiDirect,
                                onSetThemeMode = viewModel::setThemeMode,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
