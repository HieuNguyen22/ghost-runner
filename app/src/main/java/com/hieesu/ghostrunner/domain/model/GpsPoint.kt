package com.hieesu.ghostrunner.domain.model

/**
 * Represents a single GPS coordinate point with associated movement metadata.
 * Used throughout the pipeline: from route generation to mock location dispatch.
 */
data class GpsPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val speed: Float = 0f,         // m/s
    val bearing: Float = 0f,       // degrees (0-360)
    val accuracy: Float = 3f,      // meters
    val timestamp: Long = System.currentTimeMillis()
)
