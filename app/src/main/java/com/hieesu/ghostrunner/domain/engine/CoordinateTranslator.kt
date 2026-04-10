package com.hieesu.ghostrunner.domain.engine

import com.hieesu.ghostrunner.domain.model.PathSegment
import javax.inject.Inject
import kotlin.math.cos

/**
 * Translates relative (x, y) coordinates (in meters) to absolute GPS (latitude, longitude).
 *
 * Uses Flat-Earth approximation which is accurate to < 0.1% for distances < 10km.
 * For GPS Art, typical routes are 1-10km, well within the accuracy range.
 *
 * Formulas:
 *   newLat = startLat + (dy / EARTH_RADIUS) × (180 / π)
 *   newLng = startLng + (dx / (EARTH_RADIUS × cos(startLat × π/180))) × (180 / π)
 *
 * Where:
 *   dx = x offset in meters (positive = East)
 *   dy = y offset in meters (positive = South, inverted to North for lat)
 */
class CoordinateTranslator @Inject constructor() {

    companion object {
        const val EARTH_RADIUS = 6_371_000.0 // meters
    }

    /**
     * Convert relative meter-based path segments to GPS coordinate path segments.
     *
     * Note: The y-axis in our coordinate system points downward (screen coordinates),
     * but latitude increases northward. We negate dy to account for this.
     *
     * @param segments Scaled path segments with coordinates in meters
     * @param startLat Starting latitude
     * @param startLng Starting longitude
     * @return Path segments with (latitude, longitude) coordinates
     */
    fun translate(
        segments: List<PathSegment>,
        startLat: Double,
        startLng: Double
    ): List<PathSegment> {
        val latRadians = Math.toRadians(startLat)
        val metersPerDegreeLat = (Math.PI / 180.0) * EARTH_RADIUS
        val metersPerDegreeLng = (Math.PI / 180.0) * EARTH_RADIUS * cos(latRadians)

        return segments.map { segment ->
            PathSegment(
                points = segment.points.map { (x, y) ->
                    // x → longitude offset (East), y → latitude offset (negate: down→south)
                    val lat = startLat - (y / metersPerDegreeLat)
                    val lng = startLng + (x / metersPerDegreeLng)
                    lat to lng
                },
                type = segment.type
            )
        }
    }

    /**
     * Convert a single (x, y) meter offset to GPS coordinates.
     */
    fun translatePoint(
        x: Double,
        y: Double,
        startLat: Double,
        startLng: Double
    ): Pair<Double, Double> {
        val latRadians = Math.toRadians(startLat)
        val metersPerDegreeLat = (Math.PI / 180.0) * EARTH_RADIUS
        val metersPerDegreeLng = (Math.PI / 180.0) * EARTH_RADIUS * cos(latRadians)

        val lat = startLat - (y / metersPerDegreeLat)
        val lng = startLng + (x / metersPerDegreeLng)
        return lat to lng
    }
}
