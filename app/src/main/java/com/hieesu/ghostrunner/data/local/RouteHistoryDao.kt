package com.hieesu.ghostrunner.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for route history operations.
 */
@Dao
interface RouteHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: RouteHistoryEntity): Long

    @Update
    suspend fun updateRoute(route: RouteHistoryEntity)

    @Delete
    suspend fun deleteRoute(route: RouteHistoryEntity)

    @Query("SELECT * FROM route_history ORDER BY createdAt DESC")
    fun getAllRoutes(): Flow<List<RouteHistoryEntity>>

    @Query("SELECT * FROM route_history WHERE id = :id")
    suspend fun getRouteById(id: Long): RouteHistoryEntity?

    @Query("UPDATE route_history SET status = :status, completedAt = :completedAt WHERE id = :id")
    suspend fun updateRouteStatus(id: Long, status: String, completedAt: Long? = null)

    @Query("DELETE FROM route_history")
    suspend fun deleteAllRoutes()
}
