package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.transfer.FileCategory
import com.example.transfer.FileMetadataResolver
import com.example.transfer.LocalNetworkUtils
import com.example.transfer.QrPairingCodec
import com.example.transfer.ReceiverEndpointInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

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
}
