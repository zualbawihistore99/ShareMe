package com.example.transfer

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream

data class PendingSavedFile(
    val uri: Uri,
    val outputStream: OutputStream,
    val finalizeWrite: (Boolean) -> Unit
)

object FileSaver {

    fun openDestination(
        context: Context,
        fileName: String,
        mimeType: String
    ): PendingSavedFile? {
        val safeName = sanitizeFileName(fileName)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openMediaStoreDestination(context, safeName, mimeType)
        } else {
            openAppExternalDestination(context, safeName)
        }
    }

    private fun openMediaStoreDestination(
        context: Context,
        fileName: String,
        mimeType: String
    ): PendingSavedFile? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val resolver = context.contentResolver
        val category = FileMetadataResolver.categorize(mimeType, fileName)

        val (collection, relativePath) = when (category) {
            FileCategory.PHOTO ->
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to
                    "${Environment.DIRECTORY_PICTURES}/ShareMe"
            FileCategory.VIDEO ->
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to
                    "${Environment.DIRECTORY_MOVIES}/ShareMe"
            FileCategory.AUDIO ->
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to
                    "${Environment.DIRECTORY_MUSIC}/ShareMe"
            else ->
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to
                    "${Environment.DIRECTORY_DOWNLOADS}/ShareMe"
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType.ifBlank { "application/octet-stream" })
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = resolver.insert(collection, values) ?: return openAppExternalDestination(context, fileName)
        val stream = runCatching { resolver.openOutputStream(uri) }.getOrNull()
        if (stream == null) {
            runCatching { resolver.delete(uri, null, null) }
            return openAppExternalDestination(context, fileName)
        }

        return PendingSavedFile(
            uri = uri,
            outputStream = stream,
            finalizeWrite = { success ->
                runCatching { stream.close() }
                if (success) {
                    val doneValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    runCatching { resolver.update(uri, doneValues, null, null) }
                } else {
                    runCatching { resolver.delete(uri, null, null) }
                }
            }
        )
    }

    private fun openAppExternalDestination(
        context: Context,
        fileName: String
    ): PendingSavedFile? {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val shareMeDir = File(baseDir, "ShareMe").apply { mkdirs() }
        var target = File(shareMeDir, fileName)
        if (target.exists()) {
            val dot = fileName.lastIndexOf('.')
            val stem = if (dot > 0) fileName.substring(0, dot) else fileName
            val ext = if (dot > 0) fileName.substring(dot) else ""
            target = File(shareMeDir, "${stem}_${System.currentTimeMillis()}$ext")
        }

        val stream = runCatching { target.outputStream() }.getOrNull() ?: return null
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, target)

        return PendingSavedFile(
            uri = uri,
            outputStream = stream,
            finalizeWrite = { success ->
                runCatching { stream.close() }
                if (!success) {
                    runCatching { target.delete() }
                }
            }
        )
    }

    fun openFile(context: Context, uriString: String, mimeType: String): Boolean {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(Intent.createChooser(intent, "Open with"))
            true
        }.getOrDefault(false)
    }

    fun shareFileViaChooser(context: Context, uriString: String, mimeType: String): Boolean {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share file").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    private fun sanitizeFileName(raw: String): String {
        val trimmed = raw.substringAfterLast('/').substringAfterLast('\\').trim()
        return trimmed.replace(Regex("[^a-zA-Z0-9._\\- ()]"), "_")
            .ifBlank { "shared_file_${System.currentTimeMillis()}" }
    }
}
