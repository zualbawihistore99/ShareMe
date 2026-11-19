package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TransferRecordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ShareMeDatabase : RoomDatabase() {
    abstract fun transferDao(): TransferDao

    companion object {
        fun create(context: Context): ShareMeDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                ShareMeDatabase::class.java,
                "shareme_transfers.db"
            ).build()
    }
}
