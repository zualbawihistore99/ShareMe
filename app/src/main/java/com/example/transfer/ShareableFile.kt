package com.example.transfer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.util.UUID

enum class FileCategory {
    PHOTO,
    VIDEO,
    AUDIO,
    DOCUMENT,
    OTHER
}

data class ShareableFile(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val category: FileCategory
)

object FileMetadataResolver {

    fun resolve(context: Context, uri: Uri): ShareableFile? {
        val resolver = context.contentResolver
        val rawMime = resolver.getType(uri)?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
        var displayName = "shared_file_${System.currentTimeMillis()}"
        var sizeBytes = 0L

        runCatching {
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) {
                        cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let {
                            displayName = it
                        }
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        sizeBytes = cursor.getLong(sizeIdx).coerceAtLeast(0L)
                    }
                }
            }
        }

        if (sizeBytes <= 0L) {
            runCatching {
                resolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    if (afd.length > 0L) sizeBytes = afd.length
                }
            }
        }

        return ShareableFile(
            uri = uri,
            name = displayName,
            sizeBytes = sizeBytes,
            mimeType = rawMime,
            category = categorize(rawMime, displayName)
        )
    }

    fun categorize(mimeType: String, fileName: String): FileCategory {
        val lowerMime = mimeType.lowercase()
        val lowerName = fileName.lowercase()
        return when {
            lowerMime.startsWith("image/") ||
                lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
                lowerName.endsWith(".png") || lowerName.endsWith(".webp") -> FileCategory.PHOTO

            lowerMime.startsWith("video/") ||
                lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") ||
                lowerName.endsWith(".mov") || lowerName.endsWith(".webm") -> FileCategory.VIDEO

            lowerMime.startsWith("audio/") ||
                lowerName.endsWith(".mp3") || lowerName.endsWith(".wav") ||
                lowerName.endsWith(".m4a") || lowerName.endsWith(".flac") ||
                lowerName.endsWith(".ogg") -> FileCategory.AUDIO

            lowerMime.startsWith("application/pdf") ||
                lowerMime.startsWith("text/") ||
                lowerMime.contains("document") ||
                lowerMime.contains("spreadsheet") ||
                lowerMime.contains("presentation") ||
                lowerName.endsWith(".pdf") || lowerName.endsWith(".doc") ||
                lowerName.endsWith(".docx") || lowerName.endsWith(".txt") ||
                lowerName.endsWith(".zip") -> FileCategory.DOCUMENT

            else -> FileCategory.OTHER
        }
    }
}
