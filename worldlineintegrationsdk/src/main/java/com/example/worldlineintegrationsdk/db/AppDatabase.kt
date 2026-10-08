package com.example.worldlineintegrationsdk.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.worldlineintegrationsdk.db.dao.BlacklistDao
import com.example.worldlineintegrationsdk.db.dao.CardAcceptanceDao
import com.example.worldlineintegrationsdk.db.dao.FloorLimitDao
import com.example.worldlineintegrationsdk.db.dao.TableMetaDao
import com.example.worldlineintegrationsdk.db.dao.TacDao
import com.example.worldlineintegrationsdk.db.dao.TlpDao
import com.example.worldlineintegrationsdk.db.entity.AidEntity
import com.example.worldlineintegrationsdk.db.entity.BlacklistEntity
import com.example.worldlineintegrationsdk.db.entity.CapkEntity
import com.example.worldlineintegrationsdk.db.entity.CardAcceptanceEntity
import com.example.worldlineintegrationsdk.db.entity.FloorLimitEntity
import com.example.worldlineintegrationsdk.db.entity.TableMetaEntity
import com.example.worldlineintegrationsdk.db.entity.TacEntity
import com.example.worldlineintegrationsdk.db.entity.TlpFieldEntity
import com.example.worldlineintegrationsdk.db.entity.TlpTableEntity

@Database(
    entities = [
        BlacklistEntity::class,
        FloorLimitEntity::class,
        TableMetaEntity::class,
        TacEntity::class,
        TlpTableEntity::class,
        TlpFieldEntity::class,
        AidEntity::class,
        CapkEntity::class,
        CardAcceptanceEntity::class,
    ],
    version = 7,           // 1 -> 2
    exportSchema = false
)
 abstract class AppDatabase : RoomDatabase() {

    abstract fun blacklistDao(): BlacklistDao

    abstract fun floorLimitDao(): FloorLimitDao

    abstract fun tableMetaDao(): TableMetaDao

    abstract fun tacDao(): TacDao

    abstract fun tlpDao(): TlpDao

    abstract fun refDao(): RefDao

    abstract fun cardAcceptanceDao(): CardAcceptanceDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {

            return INSTANCE ?: synchronized(this) {

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "worldline_database"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
                INSTANCE = instance

                instance
            }
        }
    }
}