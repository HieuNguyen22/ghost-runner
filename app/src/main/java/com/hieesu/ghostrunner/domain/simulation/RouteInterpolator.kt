package com.hieesu.ghostrunner.domain.simulation

import com.hieesu.ghostrunner.domain.model.GpsPoint
import com.hieesu.ghostrunner.domain.model.PathSegment
import com.hieesu.ghostrunner.domain.model.SegmentType
import javax.inject.Inject
import kotlin.math.hypot
import kotlin.math.max

/**
 * Interpolates between GPS points to create smooth, closely-spaced waypoints
 * suitable for realistic mock location playback.
 *
 * Uses Catmull-Rom spline interpolation for PEN_DOWN segments (drawing strokes)
 * and linear interpolation for PEN_UP segments (transitions).
 */
class RouteInterpolator @Inject constructor() {

    companion object {
        /** Target distance between interpolated points for PEN_DOWN segments (meters) */
        const val PEN_DOWN_INTERVAL_METERS = 5.0

        /** Target distance between interpolated points for PEN_UP segments (meters) */
        const val PEN_UP_INTERVAL_METERS = 20.0

        /** Approximate conversion: 1 degree latitude ≈ 111,320 meters */
        const val METERS_PER_DEGREE_LAT = 111_320.0
    }

    /**
     * Interpolate path segments to create smooth GPS point sequences.
     *
     * @param segments GPS-coordinate path segments
     * @return Flat list of interpolated [GpsPoint]s
     */
    fun interpolate(segments: List<PathSegment>): List<GpsPoint> {
        val result = mutableListOf<GpsPoint>()

        for (segment in segments) {
            if (segment.points.size < 2) {
                // Single point segment — just add it
                segment.points.forEach { (lat, lng) ->
                    result.add(GpsPoint(latitude = lat, longitude = lng))
                }
                continue
            }

            val interval = when (segment.type) {
                SegmentType.PEN_DOWN -> PEN_DOWN_INTERVAL_METERS
                SegmentType.PEN_UP -> PEN_UP_INTERVAL_METERS
            }

            when (segment.type) {
                SegmentType.PEN_DOWN -> {
                    result.addAll(catmullRomInterpolate(segment.points, interval))
                }
                SegmentType.PEN_UP -> {
                    result.addAll(linearInterpolate(segment.points, interval))
                }
            }
        }

        return result
    }

    /**
     * Catmull-Rom spline interpolation for smooth curves through control points.
     */
    private fun catmullRomInterpolate(
        points: List<Pair<Double, Double>>,
        intervalMeters: Double
    ): List<GpsPoint> {
        val result = mutableListOf<GpsPoint>()

        for (i in 0 until points.size - 1) {
            val p0 = if (i > 0) points[i - 1] else points[i]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = if (i + 2 < points.size) points[i + 2] else points[i + 1]

            val segmentDist = distanceMeters(p1, p2)
            val steps = max(2, (segmentDist / intervalMeters).toInt())

            for (step in 0 until steps) {
                val t = step.toDouble() / steps

                val lat = catmullRomValue(t, p0.first, p1.first, p2.first, p3.first)
                val lng = catmullRomValue(t, p0.second, p1.second, p2.second, p3.second)

                result.add(GpsPoint(latitude = lat, longitude = lng))
            }
        }

        // Add the last point
        val last = points.last()
        result.add(GpsPoint(latitude = last.first, longitude = last.second))

        return result
    }

    /**
     * Catmull-Rom spline value at parameter t.
     * Uses the standard matrix form with tension = 0.5.
     */
    private fun catmullRomValue(t: Double, p0: Double, p1: Double, p2: Double, p3: Double): Double {
        val t2 = t * t
        val t3 = t2 * t
        return 0.5 * (
            (2 * p1) +
            (-p0 + p2) * t +
            (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 +
            (-p0 + 3 * p1 - 3 * p2 + p3) * t3
        )
    }

    /**
     * Linear interpolation between points — used for PEN_UP transitions.
     */
    private fun linearInterpolate(
        points: List<Pair<Double, Double>>,
        intervalMeters: Double
    ): List<GpsPoint> {
        val result = mutableListOf<GpsPoint>()

        for (i in 0 until points.size - 1) {
            val (lat1, lng1) = points[i]
            val (lat2, lng2) = points[i + 1]

            val segmentDist = distanceMeters(points[i], points[i + 1])
            val steps = max(2, (segmentDist / intervalMeters).toInt())

            for (step in 0 until steps) {
                val t = step.toDouble() / steps
                val lat = lat1 + (lat2 - lat1) * t
                val lng = lng1 + (lng2 - lng1) * t
                result.add(GpsPoint(latitude = lat, longitude = lng))
            }
        }

        val last = points.last()
        result.add(GpsPoint(latitude = last.first, longitude = last.second))

        return result
    }

    /**
     * Approximate distance in meters between two GPS points using Euclidean on lat/lng.
     */
    private fun distanceMeters(p1: Pair<Double, Double>, p2: Pair<Double, Double>): Double {
        val dLat = (p2.first - p1.first) * METERS_PER_DEGREE_LAT
        val dLng = (p2.second - p1.second) * METERS_PER_DEGREE_LAT *
                   Math.cos(Math.toRadians(p1.first))
        return hypot(dLat, dLng)
    }
}
