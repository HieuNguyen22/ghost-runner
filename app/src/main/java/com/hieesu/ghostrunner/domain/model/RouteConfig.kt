package com.hieesu.ghostrunner.domain.model

/**
 * Configuration for a GPS Art route generation request.
 */
data class RouteConfig(
    val text: String,                    // e.g. "HELLO"
    val startLatitude: Double,           // Starting point latitude
    val startLongitude: Double,          // Starting point longitude
    val totalDistanceKm: Double,         // Target total distance in km (e.g. 5.0)
    val totalDurationMinutes: Int        // Target total time in minutes (e.g. 120)
)
