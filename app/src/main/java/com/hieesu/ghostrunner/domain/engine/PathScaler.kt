package com.hieesu.ghostrunner.domain.engine

import com.hieesu.ghostrunner.domain.model.PathBounds
import com.hieesu.ghostrunner.domain.model.PathResult
import com.hieesu.ghostrunner.domain.model.PathSegment
import javax.inject.Inject
import kotlin.math.hypot

/**
 * Scales a relative path so its total PEN_DOWN distance matches a target distance in meters.
 *
 * After scaling, each coordinate unit in the path represents 1 meter in the real world.
 */
class PathScaler @Inject constructor() {

    /**
     * Scale the path to match the target distance.
     *
     * @param pathResult The unscaled path result from [TextToPathEngine]
     * @param targetDistanceMeters The desired total PEN_DOWN distance in meters
     * @return A new [PathResult] with coordinates scaled to meters
     */
    fun scalePath(pathResult: PathResult, targetDistanceMeters: Double): PathResult {
        if (pathResult.totalDrawDistance <= 0.0) {
            return pathResult
        }

        val scaleFactor = targetDistanceMeters / pathResult.totalDrawDistance

        val scaledSegments = pathResult.segments.map { segment ->
            PathSegment(
                points = segment.points.map { (x, y) ->
                    (x * scaleFactor) to (y * scaleFactor)
                },
                type = segment.type
            )
        }

        // Recalculate distances with scaled coordinates
        var totalDraw = 0.0
        var totalMove = 0.0
        for (segment in scaledSegments) {
            val dist = calculateDistance(segment.points)
            when (segment.type) {
                com.hieesu.ghostrunner.domain.model.SegmentType.PEN_DOWN -> totalDraw += dist
                com.hieesu.ghostrunner.domain.model.SegmentType.PEN_UP -> totalMove += dist
            }
        }

        val bounds = pathResult.bounds
        val scaledBounds = PathBounds(
            minX = bounds.minX * scaleFactor,
            minY = bounds.minY * scaleFactor,
            maxX = bounds.maxX * scaleFactor,
            maxY = bounds.maxY * scaleFactor
        )

        return PathResult(
            segments = scaledSegments,
            totalDrawDistance = totalDraw,
            totalMoveDistance = totalMove,
            bounds = scaledBounds
        )
    }

    private fun calculateDistance(points: List<Pair<Double, Double>>): Double {
        var dist = 0.0
        for (i in 0 until points.size - 1) {
            val (x1, y1) = points[i]
            val (x2, y2) = points[i + 1]
            dist += hypot(x2 - x1, y2 - y1)
        }
        return dist
    }
}
