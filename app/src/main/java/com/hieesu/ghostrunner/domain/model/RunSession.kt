package com.hieesu.ghostrunner.domain.model

/**
 * Represents a complete run session with all generated route data and current state.
 */
data class RunSession(
    val config: RouteConfig,
    val rawPath: List<PathSegment>,            // Relative (x,y) path segments
    val gpsRoute: List<GpsPoint>,              // GPS coordinates (clean, no jitter)
    val simulatedRoute: List<GpsPoint>,        // Final: interpolation + jitter + pace
    val state: RunState = RunState.IDLE,
    val currentIndex: Int = 0,
    val totalDistanceMeters: Double = 0.0,
    val totalDurationMs: Long = 0L
)

enum class RunState {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETED
}
