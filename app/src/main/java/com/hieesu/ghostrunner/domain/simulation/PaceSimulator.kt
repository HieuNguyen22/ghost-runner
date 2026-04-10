package com.hieesu.ghostrunner.domain.simulation

import com.hieesu.ghostrunner.domain.model.GpsPoint
import javax.inject.Inject
import java.util.Random
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Simulates realistic running pace by assigning timestamps and speeds to GPS points.
 *
 * Features:
 * - Base pace calculated from total distance / duration
 * - Warm-up phase: slower speed in first 500m
 * - Cool-down phase: slower speed in last 300m
 * - Natural speed variation: ±15% using smooth noise
 * - Micro-pauses: brief stops every 800-1500m (simulating traffic lights, water breaks)
 */
class PaceSimulator @Inject constructor() {

    companion object {
        /** Warm-up distance in meters */
        const val WARMUP_DISTANCE = 500.0

        /** Cool-down distance in meters */
        const val COOLDOWN_DISTANCE = 300.0

        /** Warm-up speed multiplier (20% slower) */
        const val WARMUP_FACTOR = 0.80

        /** Cool-down speed multiplier (15% slower) */
        const val COOLDOWN_FACTOR = 0.85

        /** Maximum speed variation factor (±15%) */
        const val SPEED_VARIATION = 0.15

        /** Minimum distance between micro-pauses (meters) */
        const val MIN_PAUSE_INTERVAL = 800.0

        /** Maximum distance between micro-pauses (meters) */
        const val MAX_PAUSE_INTERVAL = 1500.0

        /** Minimum micro-pause duration (ms) */
        const val MIN_PAUSE_DURATION_MS = 2000L

        /** Maximum micro-pause duration (ms) */
        const val MAX_PAUSE_DURATION_MS = 5000L

        /** Meters per degree latitude (approximate) */
        const val METERS_PER_DEGREE = 111_320.0
    }

    private val random = Random()

    /**
     * Assign timestamps and speeds to GPS points to simulate realistic movement.
     *
     * @param points Interpolated GPS points (with jitter already applied)
     * @param totalDurationMs Target total run duration in milliseconds
     * @param totalDistanceMeters Target total distance in meters
     * @return Points with realistic timestamps, speeds, and bearings
     */
    fun simulatePace(
        points: List<GpsPoint>,
        totalDurationMs: Long,
        totalDistanceMeters: Double
    ): List<GpsPoint> {
        if (points.size < 2) return points

        // Calculate distances between consecutive points
        val distances = mutableListOf<Double>()
        for (i in 0 until points.size - 1) {
            distances.add(distanceBetween(points[i], points[i + 1]))
        }

        val actualTotalDistance = distances.sum()
        if (actualTotalDistance <= 0) return points

        // Base speed in m/s
        val baseSpeed = totalDistanceMeters / (totalDurationMs / 1000.0)

        // Plan micro-pauses
        val pauses = planMicroPauses(actualTotalDistance)
        val totalPauseTime = pauses.sumOf { it.second }

        // Adjust base speed to account for pause time
        val activeTime = max(1.0, (totalDurationMs - totalPauseTime) / 1000.0)
        val adjustedBaseSpeed = totalDistanceMeters / activeTime

        // Generate smooth speed variation noise
        val speedNoise = generateSmoothNoise(points.size)

        val result = mutableListOf<GpsPoint>()
        var currentTimeMs = System.currentTimeMillis()
        var cumulativeDistance = 0.0

        for (i in points.indices) {
            val point = points[i]

            // Calculate phase-based speed modifier
            val phaseModifier = when {
                cumulativeDistance < WARMUP_DISTANCE -> WARMUP_FACTOR
                cumulativeDistance > (actualTotalDistance - COOLDOWN_DISTANCE) -> COOLDOWN_FACTOR
                else -> 1.0
            }

            // Apply speed variation noise
            val noiseModifier = 1.0 + (speedNoise[i] * SPEED_VARIATION)

            // Final speed
            val speed = (adjustedBaseSpeed * phaseModifier * noiseModifier).toFloat()
                .coerceIn(0.5f, adjustedBaseSpeed.toFloat() * 2f) // Safety clamp

            // Calculate bearing to next point
            val bearing = if (i < points.size - 1) {
                bearingBetween(point, points[i + 1])
            } else if (i > 0) {
                bearingBetween(points[i - 1], point)
            } else {
                0f
            }

            result.add(
                point.copy(
                    speed = speed,
                    bearing = bearing,
                    timestamp = currentTimeMs
                )
            )

            // Calculate time to next point
            if (i < distances.size) {
                val distance = distances[i]
                val timeForSegment = if (speed > 0) {
                    (distance / speed * 1000).toLong()
                } else {
                    1000L
                }
                currentTimeMs += max(100L, timeForSegment)
                cumulativeDistance += distance

                // Check if a micro-pause should happen
                for ((pauseAtDistance, pauseDuration) in pauses) {
                    if (cumulativeDistance - distance < pauseAtDistance &&
                        cumulativeDistance >= pauseAtDistance
                    ) {
                        currentTimeMs += pauseDuration
                    }
                }
            }
        }

        return result
    }

    /**
     * Plan micro-pauses at random intervals along the route.
     *
     * @return List of (distanceFromStart, pauseDurationMs)
     */
    private fun planMicroPauses(totalDistance: Double): List<Pair<Double, Long>> {
        val pauses = mutableListOf<Pair<Double, Long>>()
        var currentDist = MIN_PAUSE_INTERVAL +
            random.nextDouble() * (MAX_PAUSE_INTERVAL - MIN_PAUSE_INTERVAL)

        while (currentDist < totalDistance - COOLDOWN_DISTANCE) {
            val duration = MIN_PAUSE_DURATION_MS +
                (random.nextDouble() * (MAX_PAUSE_DURATION_MS - MIN_PAUSE_DURATION_MS)).toLong()
            pauses.add(currentDist to duration)

            currentDist += MIN_PAUSE_INTERVAL +
                random.nextDouble() * (MAX_PAUSE_INTERVAL - MIN_PAUSE_INTERVAL)
        }

        return pauses
    }

    /**
     * Generate smooth noise values between -1 and 1 using simple moving average approach.
     */
    private fun generateSmoothNoise(size: Int): List<Double> {
        if (size == 0) return emptyList()

        // Generate raw random noise
        val raw = List(size) { random.nextGaussian() * 0.5 }

        // Smooth with a moving window
        val windowSize = max(1, size / 20) // ~5% of total points
        val smoothed = mutableListOf<Double>()

        for (i in raw.indices) {
            val start = max(0, i - windowSize)
            val end = min(raw.size - 1, i + windowSize)
            val avg = raw.subList(start, end + 1).average()
            smoothed.add(avg.coerceIn(-1.0, 1.0))
        }

        return smoothed
    }

    /**
     * Calculate bearing between two GPS points in degrees.
     */
    private fun bearingBetween(from: GpsPoint, to: GpsPoint): Float {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLng = Math.toRadians(to.longitude - from.longitude)

        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)

        var bearing = Math.toDegrees(atan2(y, x)).toFloat()
        if (bearing < 0) bearing += 360f

        return bearing
    }

    /**
     * Distance between two GPS points in meters.
     */
    private fun distanceBetween(p1: GpsPoint, p2: GpsPoint): Double {
        val dLat = (p2.latitude - p1.latitude) * METERS_PER_DEGREE
        val dLng = (p2.longitude - p1.longitude) * METERS_PER_DEGREE *
                   cos(Math.toRadians(p1.latitude))
        return hypot(dLat, dLng)
    }
}
