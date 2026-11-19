package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transfer_records")
data class TransferRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val fileName: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val direction: String,
    val status: String,
    val peerName: String,
    val transportMethod: String,
    val localUri: String?,
    val averageSpeedBytesPerSec: Long,
    val timestampMillis: Long = System.currentTimeMillis()
)
