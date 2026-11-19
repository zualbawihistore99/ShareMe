package com.example

import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppThemeMode
import com.example.data.local.DevicePreferences
import com.example.data.local.DevicePreferencesRepository
import com.example.transfer.FileCategory
import com.example.transfer.FileMetadataResolver
import com.example.transfer.LocalNetworkUtils
import com.example.transfer.NearbyPeer
import com.example.transfer.PeerTransport
import com.example.transfer.QrPairingCodec
import com.example.transfer.ReceiverEndpointInfo
import com.example.transfer.TransferSessionState
import com.example.ui.components.RadarPulseCanvas
import com.example.ui.screens.SendScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.ShareMeTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp")
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `app name matches ShareMe and package name is configured`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ShareMe", appName)
        assertEquals("com.aistudio.shareme.k7m9xp", context.packageName)
    }

    @Test
    fun `file categorization and byte formatting work accurately`() {
        assertEquals(FileCategory.PHOTO, FileMetadataResolver.categorize("image/jpeg", "vacation.jpg"))
        assertEquals(FileCategory.VIDEO, FileMetadataResolver.categorize("video/mp4", "clip.mp4"))
        assertEquals(FileCategory.AUDIO, FileMetadataResolver.categorize("audio/mpeg", "song.mp3"))
        assertEquals(FileCategory.DOCUMENT, FileMetadataResolver.categorize("application/pdf", "notes.pdf"))
        assertEquals("1.0 MB", LocalNetworkUtils.formatBytes(1024L * 1024L))
        assertEquals("0:45", LocalNetworkUtils.formatEta(45L))

        val perms = LocalNetworkUtils.requiredDiscoveryPermissions().toSet()
        assert(perms.contains(android.Manifest.permission.NEARBY_WIFI_DEVICES))
        assert(perms.contains(android.Manifest.permission.BLUETOOTH_SCAN))
        assert(perms.contains(android.Manifest.permission.BLUETOOTH_CONNECT))
        assert(perms.contains(android.Manifest.permission.BLUETOOTH_ADVERTISE))
    }

    @Test
    fun `qr pairing codec encodes parses and generates bitmap`() {
        val endpoint = ReceiverEndpointInfo(
            isListening = true,
            deviceName = "Pixel Share",
            localIp = "192.168.1.42",
            port = 8988,
            sessionPin = "4821",
            bluetoothAddress = "AA:BB:CC:DD:EE:FF"
        )
        val encoded = QrPairingCodec.encode(endpoint)
        val decoded = QrPairingCodec.parse(encoded)
        assertNotNull(decoded)
        assertEquals("192.168.1.42", decoded?.ip)
        assertEquals(8988, decoded?.port)
        assertEquals("4821", decoded?.pin)
        assertEquals("Pixel Share", decoded?.deviceName)
        assertEquals("AA:BB:CC:DD:EE:FF", decoded?.bluetoothAddress)

        assertNull(QrPairingCodec.parse("https://example.com/not-shareme"))

        val bitmap = QrPairingCodec.generateQrBitmap(encoded, 256)
        assertEquals(256, bitmap.width)
        assertEquals(256, bitmap.height)
    }

    @Test
    fun `wifi enable intent uses system panel on Android 10 plus`() {
        val intent = LocalNetworkUtils.createEnableWifiIntent()
        assertEquals(android.provider.Settings.Panel.ACTION_WIFI, intent.action)
    }

    @Test
    fun `day and night theme preference persists in DataStore`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = DevicePreferencesRepository(context)

        repo.setThemeMode(AppThemeMode.NIGHT)
        assertEquals(AppThemeMode.NIGHT, repo.preferences.first().themeMode)

        repo.setThemeMode(AppThemeMode.DAY)
        assertEquals(AppThemeMode.DAY, repo.preferences.first().themeMode)

        repo.setThemeMode(AppThemeMode.SYSTEM)
        assertEquals(AppThemeMode.SYSTEM, repo.preferences.first().themeMode)
    }

    @Test
    fun `radar shows discovered receiver phones and triggers click callback`() {
        val peer = NearbyPeer(
            id = "wifi:192.168.1.88:8988",
            name = "Galaxy Receiver",
            transport = PeerTransport.LOCAL_WIFI,
            ipAddress = "192.168.1.88",
            port = 8988,
            statusLabel = "Wi-Fi LAN • Ready"
        )
        var clickedPeer: NearbyPeer? = null

        composeRule.setContent {
            ShareMeTheme(darkTheme = false) {
                RadarPulseCanvas(
                    isActive = true,
                    accentColor = CobaltPrimary,
                    deviceName = "My Phone",
                    avatarColorIndex = 0,
                    peers = listOf(peer),
                    selectedPeerId = null,
                    onPeerClick = { clickedPeer = it }
                )
            }
        }

        composeRule.onNodeWithTag("radar_peer_${peer.id}")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        assertEquals(peer.id, clickedPeer?.id)
    }

    @Test
    fun `send screen ready to share card is clickable and displays radar receivers`() {
        val peer = NearbyPeer(
            id = "bt:11:22:33:44:55:66",
            name = "Receiver Two",
            transport = PeerTransport.BLUETOOTH,
            bluetoothAddress = "11:22:33:44:55:66",
            statusLabel = "Bluetooth • Ready"
        )

        composeRule.setContent {
            ShareMeTheme(darkTheme = true) {
                SendScreen(
                    devicePreferences = DevicePreferences(
                        deviceName = "Sender Phone",
                        avatarColorIndex = 0,
                        autoAcceptIncoming = false,
                        preferWifiDirect = true,
                        themeMode = AppThemeMode.NIGHT
                    ),
                    queuedFiles = emptyList(),
                    discoveredPeers = listOf(peer),
                    pairedPeer = null,
                    isScanning = true,
                    discoveryStatus = "Scanning via Wi-Fi & Bluetooth...",
                    wifiEnabled = true,
                    bluetoothSupported = true,
                    bluetoothEnabled = true,
                    sessionState = TransferSessionState.Idle,
                    onAddPickedUris = { _, _ -> },
                    onRemoveFile = {},
                    onClearQueue = {},
                    onClearPairedPeer = {},
                    onStartScan = {},
                    onStopScan = {},
                    onRefreshRadioStates = {},
                    onPeerClicked = { _, _ -> },
                    onSendFromQr = { _, _ -> },
                    onSendDirectToIp = { _, _, _ -> },
                    onCancelTransfer = {},
                    onResetTransferState = {}
                )
            }
        }

        composeRule.onNodeWithTag("ready_to_share_card")
            .assertIsDisplayed()
            .assertHasClickAction()

        composeRule.onNodeWithTag("send_lazy_column")
            .performScrollToNode(hasTestTag("radar_peer_${peer.id}"))

        composeRule.onNodeWithTag("radar_peer_${peer.id}")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun `settings screen allows selecting day and night themes`() {
        var selectedTheme: AppThemeMode? = null

        composeRule.setContent {
            ShareMeTheme(darkTheme = false) {
                SettingsScreen(
                    devicePreferences = DevicePreferences(
                        deviceName = "Sender Phone",
                        avatarColorIndex = 0,
                        autoAcceptIncoming = false,
                        preferWifiDirect = true,
                        themeMode = AppThemeMode.DAY
                    ),
                    onUpdateDeviceName = {},
                    onUpdateAvatarColor = {},
                    onSetAutoAccept = {},
                    onSetPreferWifiDirect = {},
                    onSetThemeMode = { selectedTheme = it },
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("theme_mode_NIGHT")
            .assertIsDisplayed()
            .performClick()
        assertEquals(AppThemeMode.NIGHT, selectedTheme)

        composeRule.onNodeWithTag("theme_mode_DAY")
            .assertIsDisplayed()
            .performClick()
        assertEquals(AppThemeMode.DAY, selectedTheme)
    }
}
