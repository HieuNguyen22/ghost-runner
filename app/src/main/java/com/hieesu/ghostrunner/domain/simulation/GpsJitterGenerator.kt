package com.hieesu.ghostrunner.domain.simulation

import com.hieesu.ghostrunner.domain.model.GpsPoint
import javax.inject.Inject
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

/**
 * Adds realistic GPS jitter (noise) to route points to simulate real-world GPS inaccuracies.
 *
 * Real GPS signals have inherent noise due to atmospheric conditions, multipath effects,
 * and receiver limitations. Typical outdoor GPS accuracy is 3-8 meters.
 * Simulating this noise makes the route look naturally recorded.
 */
class GpsJitterGenerator @Inject constructor() {

    companion object {
        /** Standard deviation of GPS noise in meters */
        const val DEFAULT_SIGMA_METERS = 2.0

        /** Maximum random altitude variation in meters */
        const val ALTITUDE_JITTER_METERS = 2.0

        /** Minimum GPS accuracy reported (meters) */
        const val MIN_ACCURACY = 3.0f

        /** Maximum GPS accuracy reported (meters) */
        const val MAX_ACCURACY = 8.0f

        /** Bearing noise in degrees */
        const val BEARING_NOISE_DEGREES = 5.0

        /** Meters per degree latitude (approximate) */
        const val METERS_PER_DEGREE = 111_320.0
    }

    private val random = Random()

    /**
     * Apply GPS jitter to a list of GPS points.
     *
     * @param points List of clean GPS points
     * @param sigma Standard deviation of noise in meters (default: 2.0m)
     * @return List of GPS points with realistic noise applied
     */
    fun applyJitter(points: List<GpsPoint>, sigma: Double = DEFAULT_SIGMA_METERS): List<GpsPoint> {
        if (points.isEmpty()) return points

        return points.mapIndexed { index, point ->
            // Don't add jitter to the very first and last points (start/finish)
            if (index == 0 || index == points.size - 1) {
                point.copy(
                    accuracy = randomAccuracy(),
                    altitude = randomAltitude(point.altitude)
                )
            } else {
                applyJitterToPoint(point, sigma)
            }
        }
    }

    private fun applyJitterToPoint(point: GpsPoint, sigma: Double): GpsPoint {
        // Generate random displacement using Gaussian distribution
        // Use polar coordinates for uniform distribution in all directions
        val angle = random.nextDouble() * 2 * Math.PI
        val distance = random.nextGaussian() * sigma // Gaussian-distributed distance

        // Convert meter displacement to lat/lng degrees
        val dLat = distance * cos(angle) / METERS_PER_DEGREE
        val dLng = distance * sin(angle) / (METERS_PER_DEGREE * cos(Math.toRadians(point.latitude)))

        // Bearing noise
        val bearingNoise = (random.nextGaussian() * BEARING_NOISE_DEGREES).toFloat()

        return point.copy(
            latitude = point.latitude + dLat,
            longitude = point.longitude + dLng,
            altitude = randomAltitude(point.altitude),
            accuracy = randomAccuracy(),
            bearing = normalizeBearing(point.bearing + bearingNoise)
        )
    }

    private fun randomAccuracy(): Float {
        return MIN_ACCURACY + random.nextFloat() * (MAX_ACCURACY - MIN_ACCURACY)
    }

    private fun randomAltitude(baseAltitude: Double): Double {
        return baseAltitude + (random.nextGaussian() * ALTITUDE_JITTER_METERS)
    }

    private fun normalizeBearing(bearing: Float): Float {
        var normalized = bearing % 360f
        if (normalized < 0) normalized += 360f
        return normalized
    }
}
