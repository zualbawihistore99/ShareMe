package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfer_records ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<TransferRecordEntity>>

    @Query("SELECT * FROM transfer_records ORDER BY timestampMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransferRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: TransferRecordEntity): Long

    @Query("DELETE FROM transfer_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transfer_records")
    suspend fun clearAll()
}
