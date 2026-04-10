package com.hieesu.ghostrunner.domain.engine

import android.util.Log
import com.google.gson.Gson
import com.hieesu.ghostrunner.domain.model.PathSegment
import com.hieesu.ghostrunner.domain.model.SegmentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Snaps GPS waypoints to real roads using OSRM (Open Source Routing Machine).
 *
 * OSRM is free, open-source, and requires no API key.
 * Uses the "driving" profile (the only one guaranteed on the public demo server).
 *
 * Strategy:
 * - Route each consecutive PAIR of waypoints (A→B, B→C, C→D, ...)
 *   rather than sending all waypoints in one request.
 *   This is more reliable and handles long segments better.
 * - Each pair-routing returns a dense list of GPS points that follow roads.
 * - Results are stitched together to form the complete road-following path.
 * - If routing fails for a pair, falls back to a straight line for that pair only.
 *
 * API: https://router.project-osrm.org/route/v1/driving/{coordinates}
 */
class RoadSnapEngine @Inject constructor() {

    companion object {
        private const val TAG = "RoadSnapEngine"

        /** OSRM public demo server — free, no key needed */
        const val OSRM_BASE_URL = "https://router.project-osrm.org"

        /**
         * Profile: "driving" is the only profile guaranteed on the public demo server.
         * "foot" and "bicycle" may NOT be available.
         * Since our goal is road-following (not path-type), driving works fine.
         */
        const val PROFILE = "driving"

        /** Connection timeout in milliseconds */
        const val CONNECT_TIMEOUT_MS = 15_000

        /** Read timeout in milliseconds */
        const val READ_TIMEOUT_MS = 20_000

        /** Meters per degree latitude (approximate) */
        const val METERS_PER_DEGREE = 111_320.0

        /**
         * Minimum distance between two consecutive waypoints to bother routing (meters).
         * Segments shorter than this are kept as-is to avoid unnecessary API calls.
         */
        const val MIN_PAIR_DISTANCE_METERS = 20.0

        /**
         * Delay between consecutive API calls to avoid rate-limiting (ms).
         */
        const val API_DELAY_MS = 200L
    }

    private val gson = Gson()

    /**
     * Snap all path segments to real roads by routing each consecutive
     * pair of waypoints individually.
     *
     * @param segments GPS-coordinate path segments (lat, lng pairs)
     * @return New path segments following real roads
     */
    suspend fun snapToRoads(segments: List<PathSegment>): List<PathSegment> {
        return segments.map { segment ->
            try {
                snapSegmentPairwise(segment)
            } catch (e: Exception) {
                Log.e(TAG, "Road-snap failed for segment: ${e.message}")
                segment
            }
        }
    }

    /**
     * Route between two GPS points via OSRM (public API).
     * Used by RouteGenerator for simple A→B road routing.
     *
     * @return List of (lat, lng) road-following points, or null on failure
     */
    suspend fun routeSingle(
        fromLat: Double, fromLng: Double,
        toLat: Double, toLng: Double
    ): List<Pair<Double, Double>>? {
        return routePair(fromLat to fromLng, toLat to toLng)
    }

    /**
     * Snap a segment by routing each consecutive pair of control points.
     * This is more reliable than sending all points at once:
     * - Avoids OSRM waypoint limits
     * - Each pair can fail independently (partial success)
     * - Better road-following for long segments
     */
    private suspend fun snapSegmentPairwise(segment: PathSegment): PathSegment {
        val controlPoints = segment.points
        if (controlPoints.size < 2) return segment

        val allRoadPoints = mutableListOf<Pair<Double, Double>>()

        for (i in 0 until controlPoints.size - 1) {
            val from = controlPoints[i]
            val to = controlPoints[i + 1]

            val pairDistance = distanceMeters(from, to)

            if (pairDistance < MIN_PAIR_DISTANCE_METERS) {
                // Too close — just use straight line
                if (allRoadPoints.isEmpty()) allRoadPoints.add(from)
                allRoadPoints.add(to)
                continue
            }

            // Route this pair via OSRM
            val roadPoints = routePair(from, to)

            if (roadPoints != null && roadPoints.size >= 2) {
                // Stitch: skip first point if it duplicates the last added point
                if (allRoadPoints.isNotEmpty()) {
                    allRoadPoints.addAll(roadPoints.drop(1))
                } else {
                    allRoadPoints.addAll(roadPoints)
                }
                Log.d(TAG, "Pair $i→${i+1}: OSRM returned ${roadPoints.size} road points")
            } else {
                // Fallback: straight line for this pair
                Log.w(TAG, "Pair $i→${i+1}: OSRM failed, using straight line")
                if (allRoadPoints.isEmpty()) allRoadPoints.add(from)
                allRoadPoints.add(to)
            }

            // Rate-limit API calls
            if (i < controlPoints.size - 2) {
                delay(API_DELAY_MS)
            }
        }

        if (allRoadPoints.isEmpty()) return segment

        return PathSegment(
            points = allRoadPoints,
            type = segment.type
        )
    }

    /**
     * Route between exactly 2 points via OSRM.
     * Returns the full road-following path between them.
     *
     * @return List of (lat, lng) road-following points, or null on failure
     */
    private suspend fun routePair(
        from: Pair<Double, Double>,
        to: Pair<Double, Double>
    ): List<Pair<Double, Double>>? = withContext(Dispatchers.IO) {
        try {
            // OSRM expects: lng,lat;lng,lat — MUST use dots for decimals (not locale commas)
            val coordString = String.format(
                java.util.Locale.US,
                "%.6f,%.6f;%.6f,%.6f",
                from.second, from.first,  // from: lng, lat
                to.second, to.first       // to: lng, lat
            )

            val urlStr = "$OSRM_BASE_URL/route/v1/$PROFILE/$coordString" +
                         "?overview=full&geometries=geojson&steps=false"

            Log.d(TAG, "OSRM request: $urlStr")

            val connection = URL(urlStr).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", "GhostRunner/1.0")
            }

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                Log.w(TAG, "OSRM returned HTTP $responseCode")
                connection.disconnect()
                return@withContext null
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val response = gson.fromJson(responseBody, OsrmResponse::class.java)

            if (response.code != "Ok" || response.routes.isNullOrEmpty()) {
                Log.w(TAG, "OSRM response code: ${response.code}")
                return@withContext null
            }

            val geometry = response.routes[0].geometry ?: return@withContext null
            val coordinates = geometry.coordinates ?: return@withContext null

            if (coordinates.isEmpty()) return@withContext null

            val routeDistance = response.routes[0].distance ?: 0.0
            Log.d(TAG, "OSRM route: ${coordinates.size} points, ${routeDistance}m")

            // OSRM returns [lng, lat] → convert to (lat, lng)
            coordinates.map { coord ->
                coord[1] to coord[0]  // lat, lng
            }
        } catch (e: Exception) {
            Log.e(TAG, "OSRM API error: ${e.message}")
            null
        }
    }

    /**
     * Calculate the total road distance of the snapped route (meters).
     */
    fun calculateTotalDistance(segments: List<PathSegment>): Double {
        return segments.sumOf { calculateSegmentDistance(it.points) }
    }

    /**
     * Calculate the total distance of only PEN_DOWN segments (meters).
     */
    fun calculateDrawDistance(segments: List<PathSegment>): Double {
        return segments
            .filter { it.type == SegmentType.PEN_DOWN }
            .sumOf { calculateSegmentDistance(it.points) }
    }

    private fun calculateSegmentDistance(points: List<Pair<Double, Double>>): Double {
        var dist = 0.0
        for (i in 0 until points.size - 1) {
            dist += distanceMeters(points[i], points[i + 1])
        }
        return dist
    }

    private fun distanceMeters(p1: Pair<Double, Double>, p2: Pair<Double, Double>): Double {
        val dLat = (p2.first - p1.first) * METERS_PER_DEGREE
        val dLng = (p2.second - p1.second) * METERS_PER_DEGREE *
                   cos(Math.toRadians(p1.first))
        return hypot(dLat, dLng)
    }
}

// ─── OSRM API Response Models ──────────────────────────────────────────

data class OsrmResponse(
    val code: String?,
    val routes: List<OsrmRoute>?
)

data class OsrmRoute(
    val geometry: OsrmGeometry?,
    val distance: Double?,     // meters
    val duration: Double?      // seconds
)

data class OsrmGeometry(
    val type: String?,
    val coordinates: List<List<Double>>?  // [[lng, lat], [lng, lat], ...]
)
