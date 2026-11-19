package com.example.data.local

import kotlinx.coroutines.flow.Flow

class TransferRepository(private val transferDao: TransferDao) {
    val allTransfers: Flow<List<TransferRecordEntity>> = transferDao.observeAll()

    fun recentTransfers(limit: Int = 5): Flow<List<TransferRecordEntity>> =
        transferDao.observeRecent(limit)

    suspend fun recordTransfer(
        fileName: String,
        mimeType: String,
        fileSizeBytes: Long,
        direction: TransferDirection,
        status: TransferStatus,
        peerName: String,
        transportMethod: String,
        localUri: String?,
        averageSpeedBytesPerSec: Long
    ) {
        transferDao.insert(
            TransferRecordEntity(
                fileName = fileName,
                mimeType = mimeType,
                fileSizeBytes = fileSizeBytes,
                direction = direction.name,
                status = status.name,
                peerName = peerName,
                transportMethod = transportMethod,
                localUri = localUri,
                averageSpeedBytesPerSec = averageSpeedBytesPerSec
            )
        )
    }

    suspend fun deleteRecord(id: Long) = transferDao.deleteById(id)

    suspend fun clearHistory() = transferDao.clearAll()
}
