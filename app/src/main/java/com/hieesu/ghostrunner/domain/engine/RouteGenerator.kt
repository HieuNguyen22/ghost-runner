package com.hieesu.ghostrunner.domain.engine

import android.util.Log
import com.hieesu.ghostrunner.domain.model.GpsPoint
import com.hieesu.ghostrunner.domain.model.PathSegment
import com.hieesu.ghostrunner.domain.model.RouteConfig
import com.hieesu.ghostrunner.domain.model.RunSession
import com.hieesu.ghostrunner.domain.model.RunState
import com.hieesu.ghostrunner.domain.model.SegmentType
import com.hieesu.ghostrunner.domain.simulation.GpsJitterGenerator
import com.hieesu.ghostrunner.domain.simulation.PaceSimulator
import com.hieesu.ghostrunner.domain.simulation.RouteInterpolator
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Orchestrator that combines all pipeline stages to generate a complete run session.
 *
 * Two modes:
 *
 * TEXT MODE (has text):
 *   Text → Scale → GPS → OSRM road-snap → Jitter(0.5m) → Pace
 *
 * RANDOM ROAD MODE (empty text):
 *   Pick a destination → OSRM route outbound → OSRM route return → Jitter → Pace
 *   - Just 2 OSRM calls = simple + reliable
 *   - Result: a natural road-following out-and-back/loop route
 *   - No forced shape — completely follows real roads
 */
class RouteGenerator @Inject constructor(
    private val textToPathEngine: TextToPathEngine,
    private val pathScaler: PathScaler,
    private val coordinateTranslator: CoordinateTranslator,
    private val roadSnapEngine: RoadSnapEngine,
    private val routeInterpolator: RouteInterpolator,
    private val gpsJitterGenerator: GpsJitterGenerator,
    private val paceSimulator: PaceSimulator
) {

    companion object {
        private const val TAG = "RouteGenerator"
        const val METERS_PER_DEGREE_LAT = 111_320.0
        const val ROAD_SNAP_JITTER_SIGMA = 0.5
        const val PARK_JITTER_SIGMA = 1.0
    }

    suspend fun generate(config: RouteConfig): RunSession {
        val targetDistanceMeters = config.totalDistanceKm * 1000.0
        val totalDurationMs = config.totalDurationMinutes * 60L * 1000L

        if (config.isParkMode) {
            return generateNghiaDoParkRoute(config, targetDistanceMeters, totalDurationMs)
        }

        val rawPath = textToPathEngine.generatePath(config.text)
        return if (rawPath.segments.isEmpty()) {
            generateRandomRoadRoute(config, targetDistanceMeters, totalDurationMs)
        } else {
            generateTextRoute(config, rawPath, targetDistanceMeters, totalDurationMs)
        }
    }

    // ─── NGHIA DO PARK MODE ─────────────────────────────────────────────

    private suspend fun generateNghiaDoParkRoute(
        config: RouteConfig,
        targetDistanceMeters: Double,
        totalDurationMs: Long
    ): RunSession {
        val routeToPark = com.hieesu.ghostrunner.domain.data.NghiaDoParkRoute.routeToPark
        val loopPoints = com.hieesu.ghostrunner.domain.data.NghiaDoParkRoute.loopPoints
        val allPoints = mutableListOf<GpsPoint>()
        var currentDistance = 0.0

        // Start from the very first coordinate in GPX (Company)
        allPoints.add(routeToPark.first())
        
        // Phase 1: Walk to park
        for (i in 0 until routeToPark.size - 1) {
            if (currentDistance >= targetDistanceMeters) break
            
            val p1 = routeToPark[i]
            val p2 = routeToPark[i + 1]
            val dLat = (p2.latitude - p1.latitude) * METERS_PER_DEGREE_LAT
            val dLng = (p2.longitude - p1.longitude) * METERS_PER_DEGREE_LAT * cos(Math.toRadians(p1.latitude))
            val dist = kotlin.math.hypot(dLat, dLng)
            
            if (currentDistance + dist >= targetDistanceMeters) {
                val remainingDist = targetDistanceMeters - currentDistance
                val ratio = remainingDist / dist
                val finalLat = p1.latitude + ratio * (p2.latitude - p1.latitude)
                val finalLng = p1.longitude + ratio * (p2.longitude - p1.longitude)
                allPoints.add(GpsPoint(finalLat, finalLng))
                currentDistance = targetDistanceMeters
                break
            } else {
                allPoints.add(p2)
                currentDistance += dist
            }
        }
        
        // Connect routeToPark and loopPoints if needed
        if (currentDistance < targetDistanceMeters) {
            val p1 = routeToPark.last()
            val p2 = loopPoints.first()
            val dLat = (p2.latitude - p1.latitude) * METERS_PER_DEGREE_LAT
            val dLng = (p2.longitude - p1.longitude) * METERS_PER_DEGREE_LAT * cos(Math.toRadians(p1.latitude))
            val dist = kotlin.math.hypot(dLat, dLng)
            
            if (currentDistance + dist >= targetDistanceMeters) {
                val remainingDist = targetDistanceMeters - currentDistance
                val ratio = remainingDist / dist
                val finalLat = p1.latitude + ratio * (p2.latitude - p1.latitude)
                val finalLng = p1.longitude + ratio * (p2.longitude - p1.longitude)
                allPoints.add(GpsPoint(finalLat, finalLng))
                currentDistance = targetDistanceMeters
            } else {
                allPoints.add(p2)
                currentDistance += dist
            }
        }

        // Phase 2: Loop around park
        var i = 0
        while (currentDistance < targetDistanceMeters) {
            val p1 = loopPoints[i % loopPoints.size]
            val p2 = loopPoints[(i + 1) % loopPoints.size]
            
            val dLat = (p2.latitude - p1.latitude) * METERS_PER_DEGREE_LAT
            val dLng = (p2.longitude - p1.longitude) * METERS_PER_DEGREE_LAT * cos(Math.toRadians(p1.latitude))
            val dist = kotlin.math.hypot(dLat, dLng)
            
            if (currentDistance + dist > targetDistanceMeters) {
                // Interpolate final point and break
                val remainingDist = targetDistanceMeters - currentDistance
                val ratio = remainingDist / dist
                val finalLat = p1.latitude + ratio * (p2.latitude - p1.latitude)
                val finalLng = p1.longitude + ratio * (p2.longitude - p1.longitude)
                allPoints.add(GpsPoint(finalLat, finalLng))
                break
            } else {
                allPoints.add(p2)
                currentDistance += dist
                i++
            }
        }

        // Pace (apply speed and timestamps without lane shifting since GPX is already naturally aligned)
        val paced = paceSimulator.simulatePace(
            points = allPoints,
            totalDurationMs = totalDurationMs,
            totalDistanceMeters = targetDistanceMeters,
            applyLaneShift = false
        )

        // Heavy jitter (apply GPS noise realistically at the end, increase for park mode)
        val simulated = gpsJitterGenerator.applyJitter(paced, PARK_JITTER_SIGMA)

        val segment = PathSegment(points = allPoints.map { Pair(it.latitude, it.longitude) }, type = SegmentType.PEN_DOWN)

        return RunSession(
            config = config,
            rawPath = listOf(segment),
            gpsRoute = allPoints,
            simulatedRoute = simulated,
            state = RunState.IDLE,
            totalDistanceMeters = targetDistanceMeters,
            totalDurationMs = totalDurationMs
        )
    }

    // ─── RANDOM ROAD MODE ───────────────────────────────────────────────

    /**
     * Generate a random road-following route when text is empty.
     *
     * Approach (ultra-simple, just 2 OSRM calls):
     * 1. Pick a random destination ~targetDistance/3 away from start
     * 2. OSRM call 1: route start → destination (outbound, following roads)
     * 3. OSRM call 2: route destination → start (return, following roads)
     * 4. Combine both legs → complete road-following route
     *
     * OSRM will naturally find different roads for outbound vs return,
     * creating a natural-looking loop rather than an out-and-back.
     *
     * @throws RouteGenerationException if OSRM cannot find a road route
     */
    private suspend fun generateRandomRoadRoute(
        config: RouteConfig,
        targetDistanceMeters: Double,
        totalDurationMs: Long
    ): RunSession {
        val startLat = config.startLatitude
        val startLng = config.startLongitude

        // Pick a random destination point at ~1/3 of target distance away
        // (outbound leg + return leg ≈ 2/3 * target, road-detour adds the rest)
        val straightLineDistance = targetDistanceMeters / 3.0
        val randomAngle = Random.nextDouble(0.0, 360.0)
        val angleRad = Math.toRadians(randomAngle)

        val destLat = startLat + (straightLineDistance * cos(angleRad)) / METERS_PER_DEGREE_LAT
        val destLng = startLng + (straightLineDistance * sin(angleRad)) /
                      (METERS_PER_DEGREE_LAT * cos(Math.toRadians(startLat)))

        Log.d(TAG, "Random route: start=(${startLat},${startLng}), " +
              "dest=(${destLat},${destLng}), angle=${randomAngle}°, " +
              "straightDist=${straightLineDistance}m")

        // OSRM call 1: outbound (start → destination)
        val outboundPoints = roadSnapEngine.routeSingle(
            startLat, startLng, destLat, destLng
        ) ?: throw RouteGenerationException(
            "Không thể tìm đường đi. Kiểm tra kết nối mạng và thử lại.\n" +
            "(OSRM API không phản hồi)"
        )

        Log.d(TAG, "Outbound: ${outboundPoints.size} road points")

        // OSRM call 2: return (destination → start)
        val returnPoints = roadSnapEngine.routeSingle(
            destLat, destLng, startLat, startLng
        ) ?: throw RouteGenerationException(
            "Tìm được đường đi nhưng không tìm được đường về. Thử lại."
        )

        Log.d(TAG, "Return: ${returnPoints.size} road points")

        // Combine: outbound + return (skip first point of return to avoid duplicate)
        val allPoints = outboundPoints + returnPoints.drop(1)

        val segment = PathSegment(points = allPoints, type = SegmentType.PEN_DOWN)
        val actualDistance = roadSnapEngine.calculateTotalDistance(listOf(segment))

        Log.d(TAG, "Total road route: ${allPoints.size} points, ${actualDistance}m")

        // Convert to GpsPoints — NO interpolation
        val gpsPoints = allPoints.map { (lat, lng) ->
            GpsPoint(latitude = lat, longitude = lng)
        }

        // Pace (apply speed, timestamps, and lane shift on smooth points)
        val paced = paceSimulator.simulatePace(
            points = gpsPoints,
            totalDurationMs = totalDurationMs,
            totalDistanceMeters = actualDistance
        )

        // Light jitter (apply GPS noise realistically at the end)
        val simulated = gpsJitterGenerator.applyJitter(paced, ROAD_SNAP_JITTER_SIGMA)

        return RunSession(
            config = config,
            rawPath = listOf(segment),
            gpsRoute = gpsPoints,
            simulatedRoute = simulated,
            state = RunState.IDLE,
            totalDistanceMeters = actualDistance,
            totalDurationMs = totalDurationMs
        )
    }

    // ─── TEXT MODE ──────────────────────────────────────────────────────

    private suspend fun generateTextRoute(
        config: RouteConfig,
        rawPath: com.hieesu.ghostrunner.domain.model.PathResult,
        targetDistanceMeters: Double,
        totalDurationMs: Long
    ): RunSession {
        val scaledPath = pathScaler.scalePath(rawPath, targetDistanceMeters)

        val gpsSegments = coordinateTranslator.translate(
            segments = scaledPath.segments,
            startLat = config.startLatitude,
            startLng = config.startLongitude
        )

        // Road-snap via OSRM
        val roadSnappedSegments = roadSnapEngine.snapToRoads(gpsSegments)
        val actualRoadDistance = roadSnapEngine.calculateTotalDistance(roadSnappedSegments)

        // Convert to GpsPoints — NO interpolation after road-snap
        val cleanGpsPoints = segmentsToGpsPoints(roadSnappedSegments)

        // Pace (apply speed, timestamps, and lane shift on smooth points)
        val paced = paceSimulator.simulatePace(
            points = cleanGpsPoints,
            totalDurationMs = totalDurationMs,
            totalDistanceMeters = actualRoadDistance
        )

        // Light jitter (apply GPS noise realistically at the end)
        val simulated = gpsJitterGenerator.applyJitter(paced, ROAD_SNAP_JITTER_SIGMA)

        return RunSession(
            config = config,
            rawPath = scaledPath.segments,
            gpsRoute = cleanGpsPoints,
            simulatedRoute = simulated,
            state = RunState.IDLE,
            totalDistanceMeters = actualRoadDistance,
            totalDurationMs = totalDurationMs
        )
    }

    // ─── HELPERS ────────────────────────────────────────────────────────

    private fun segmentsToGpsPoints(segments: List<PathSegment>): List<GpsPoint> {
        return segments.flatMap { segment ->
            segment.points.map { (lat, lng) ->
                GpsPoint(latitude = lat, longitude = lng)
            }
        }
    }
}

class RouteGenerationException(message: String) : RuntimeException(message)
