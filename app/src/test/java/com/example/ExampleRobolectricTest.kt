package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.transfer.FileCategory
import com.example.transfer.FileMetadataResolver
import com.example.transfer.LocalNetworkUtils
import com.example.transfer.QrPairingCodec
import com.example.transfer.ReceiverEndpointInfo
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.data.local.DevicePreferences
import com.example.transfer.NearbyPeer
import com.example.transfer.PeerTransport
import com.example.transfer.TransferSessionState
import com.example.transfer.TransportPreference
import com.example.ui.components.RadarPulseCanvas
import com.example.ui.screens.SendScreen
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.ShareMeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

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
    fun `radar displays clickable popup phone and invokes selection callback`() {
        val peer = NearbyPeer(
            id = "nsd:ShareMe-Galaxy",
            name = "Galaxy S24",
            transport = PeerTransport.LOCAL_WIFI,
            ipAddress = "192.168.1.50",
            statusLabel = "Wi-Fi LAN • 192.168.1.50"
        )
        var selectedPeer: NearbyPeer? = null

        composeTestRule.setContent {
            ShareMeTheme {
                RadarPulseCanvas(
                    isActive = true,
                    accentColor = CobaltPrimary,
                    deviceName = "My Phone",
                    avatarColorIndex = 0,
                    peers = listOf(peer),
                    onPeerClick = { selectedPeer = it }
                )
            }
        }

        composeTestRule.onNodeWithTag("radar_peer_${peer.id}")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        assertTrue(selectedPeer?.id == peer.id)
    }

    @Test
    fun `send screen ready to share card is clickable and radar shows only on-radar phones`() {
        val peer = NearbyPeer(
            id = "nsd:ShareMe-Pixel",
            name = "Pixel 9",
            transport = PeerTransport.LOCAL_WIFI,
            ipAddress = "192.168.1.88",
            statusLabel = "Wi-Fi LAN • 192.168.1.88"
        )
        composeTestRule.setContent {
            ShareMeTheme {
                SendScreen(
                    devicePreferences = DevicePreferences(
                        deviceName = "My Phone",
                        avatarColorIndex = 0,
                        autoAcceptIncoming = false,
                        preferWifiDirect = true,
                        transportPreference = TransportPreference.AUTO_BOTH
                    ),
                    queuedFiles = emptyList(),
                    pairedTarget = null,
                    discoveredPeers = listOf(peer),
                    isScanning = true,
                    discoveryStatus = "Scanning...",
                    wifiEnabled = true,
                    bluetoothSupported = false,
                    bluetoothEnabled = false,
                    sessionState = TransferSessionState.Idle,
                    onAddPickedUris = {},
                    onRemoveFile = {},
                    onClearQueue = {},
                    onClearPairedTarget = {},
                    onStartScan = {},
                    onStopScan = {},
                    onRefreshRadioStates = {},
                    onSendToPeer = {},
                    onSendFromQr = {},
                    onSendDirectToIp = { _, _, _ -> },
                    onCancelTransfer = {},
                    onResetTransferState = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("ready_to_share_card")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeTestRule.onNodeWithTag("send_lazy_column")
            .performScrollToNode(hasTestTag("send_radar_canvas"))
        composeTestRule.onNodeWithTag("send_radar_canvas")
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("radar_peer_${peer.id}")
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("peer_card_${peer.id}")
            .assertDoesNotExist()
    }
}
