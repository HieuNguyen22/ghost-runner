package com.hieesu.ghostrunner.domain.model

/**
 * Represents a segment of a path — either drawing (PEN_DOWN) or moving without trace (PEN_UP).
 */
data class PathSegment(
    val points: List<Pair<Double, Double>>,
    val type: SegmentType
)

enum class SegmentType {
    /** Drawing stroke — creates visible trace on map */
    PEN_DOWN,
    /** Movement without trace — transitions between strokes/characters */
    PEN_UP
}

/**
 * Result from TextToPathEngine containing all path segments and metrics.
 */
data class PathResult(
    val segments: List<PathSegment>,
    val totalDrawDistance: Double,    // Total PEN_DOWN distance
    val totalMoveDistance: Double,    // Total PEN_UP distance
    val bounds: PathBounds            // Bounding box of all points
)

/**
 * Bounding box for the generated path.
 */
data class PathBounds(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double
) {
    val width: Double get() = maxX - minX
    val height: Double get() = maxY - minY
}
