package com.hieesu.ghostrunner.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a saved route in the history.
 */
@Entity(tableName = "route_history")
data class RouteHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val text: String,
    val startLat: Double,
    val startLng: Double,
    val distanceKm: Double,
    val durationMinutes: Int,
    val routePointsJson: String,   // Serialized List<GpsPoint> as JSON
    val status: String,            // CREATED, RUNNING, COMPLETED, CANCELLED
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)
