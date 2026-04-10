package com.hieesu.ghostrunner.domain.engine

import com.hieesu.ghostrunner.domain.model.PathBounds
import com.hieesu.ghostrunner.domain.model.PathResult
import com.hieesu.ghostrunner.domain.model.PathSegment
import com.hieesu.ghostrunner.domain.model.SegmentType
import javax.inject.Inject
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Converts a text string into a series of relative (x, y) path segments.
 *
 * Each character is looked up in [LetterVectorMap], offset horizontally,
 * and connected via PEN_UP segments between strokes and characters.
 *
 * When the input text is empty/blank, returns an empty PathResult.
 * The caller (RouteGenerator) handles the empty case by generating
 * a closed-loop route directly in GPS space.
 */
class TextToPathEngine @Inject constructor() {

    /**
     * Generate a relative path from the input text.
     * Returns empty PathResult if text is blank — caller handles loop generation.
     *
     * @param text Input string (will be uppercased; unsupported chars are skipped)
     * @return [PathResult] containing all segments and distance metrics
     */
    fun generatePath(text: String): PathResult {
        val upperText = text.trim().uppercase()

        if (upperText.isBlank()) {
            return PathResult(
                segments = emptyList(),
                totalDrawDistance = 0.0,
                totalMoveDistance = 0.0,
                bounds = PathBounds(0.0, 0.0, 0.0, 0.0)
            )
        }

        val segments = mutableListOf<PathSegment>()
        var currentX = 0.0
        var lastPoint: Pair<Double, Double>? = null

        for (char in upperText) {
            val charWidth = LetterVectorMap.getCharWidth(char)

            if (char == ' ') {
                currentX += charWidth + LetterVectorMap.CHAR_SPACING
                continue
            }

            val strokes = LetterVectorMap.glyphs[char] ?: continue

            for (stroke in strokes) {
                if (stroke.isEmpty()) continue

                val offsetPoints = stroke.map { (x, y) -> (x + currentX) to y }

                if (lastPoint != null) {
                    val firstOfStroke = offsetPoints.first()
                    if (lastPoint != firstOfStroke) {
                        segments.add(
                            PathSegment(
                                points = listOf(lastPoint!!, firstOfStroke),
                                type = SegmentType.PEN_UP
                            )
                        )
                    }
                }

                segments.add(
                    PathSegment(
                        points = offsetPoints,
                        type = SegmentType.PEN_DOWN
                    )
                )

                lastPoint = offsetPoints.last()
            }

            currentX += charWidth + LetterVectorMap.CHAR_SPACING
        }

        // Calculate distances and bounds
        var totalDraw = 0.0
        var totalMove = 0.0
        var minX = Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxX = Double.MIN_VALUE
        var maxY = Double.MIN_VALUE

        for (segment in segments) {
            val distance = calculateSegmentDistance(segment.points)
            when (segment.type) {
                SegmentType.PEN_DOWN -> totalDraw += distance
                SegmentType.PEN_UP -> totalMove += distance
            }

            for ((x, y) in segment.points) {
                minX = min(minX, x)
                minY = min(minY, y)
                maxX = max(maxX, x)
                maxY = max(maxY, y)
            }
        }

        if (segments.isEmpty()) {
            return PathResult(
                segments = emptyList(),
                totalDrawDistance = 0.0,
                totalMoveDistance = 0.0,
                bounds = PathBounds(0.0, 0.0, 0.0, 0.0)
            )
        }

        return PathResult(
            segments = segments,
            totalDrawDistance = totalDraw,
            totalMoveDistance = totalMove,
            bounds = PathBounds(minX, minY, maxX, maxY)
        )
    }

    private fun calculateSegmentDistance(points: List<Pair<Double, Double>>): Double {
        var distance = 0.0
        for (i in 0 until points.size - 1) {
            val (x1, y1) = points[i]
            val (x2, y2) = points[i + 1]
            distance += hypot(x2 - x1, y2 - y1)
        }
        return distance
    }
}
