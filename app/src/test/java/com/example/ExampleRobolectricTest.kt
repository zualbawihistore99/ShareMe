package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.transfer.FileCategory
import com.example.transfer.FileMetadataResolver
import com.example.transfer.LocalNetworkUtils
import org.junit.Assert.assertEquals
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
}
