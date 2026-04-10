package com.hieesu.ghostrunner.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database for the Ghost Runner app.
 */
@Database(
    entities = [RouteHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class GhostRunnerDatabase : RoomDatabase() {
    abstract fun routeHistoryDao(): RouteHistoryDao
}
