package com.hieesu.ghostrunner.domain.engine

/**
 * Vector definitions for all supported characters (A-Z, 0-9, space).
 *
 * Each character is defined as a list of strokes. Each stroke is a list of (x, y) points
 * on a normalized grid: x ∈ [0, 1], y ∈ [0, 1.5].
 *
 * Coordinate system:
 * - Origin (0, 0) = top-left of character cell
 * - x increases to the right
 * - y increases downward (so y=1.5 is the bottom)
 *
 * Between strokes within a character: pen lifts (PEN_UP transition).
 * Between characters: pen lifts with horizontal offset.
 */
object LetterVectorMap {

    const val CHAR_WIDTH = 1.0
    const val CHAR_HEIGHT = 1.5
    const val CHAR_SPACING = 0.3
    const val SPACE_WIDTH = 0.6

    /**
     * Map of character → list of strokes.
     * Each stroke = list of (x, y) coordinate pairs.
     */
    val glyphs: Map<Char, List<List<Pair<Double, Double>>>> = mapOf(
        // ─── LETTERS ───────────────────────────────────────────────────

        'A' to listOf(
            listOf(0.0 to 1.5, 0.5 to 0.0, 1.0 to 1.5),
            listOf(0.25 to 0.75, 0.75 to 0.75)
        ),

        'B' to listOf(
            listOf(
                0.0 to 1.5, 0.0 to 0.0,
                0.7 to 0.0, 0.9 to 0.15, 0.9 to 0.6, 0.7 to 0.75,
                0.0 to 0.75,
                0.7 to 0.75, 0.9 to 0.9, 0.9 to 1.35, 0.7 to 1.5,
                0.0 to 1.5
            )
        ),

        'C' to listOf(
            listOf(
                1.0 to 0.3, 0.7 to 0.0, 0.3 to 0.0, 0.0 to 0.3,
                0.0 to 1.2, 0.3 to 1.5, 0.7 to 1.5, 1.0 to 1.2
            )
        ),

        'D' to listOf(
            listOf(
                0.0 to 1.5, 0.0 to 0.0,
                0.6 to 0.0, 0.9 to 0.3, 0.9 to 1.2, 0.6 to 1.5,
                0.0 to 1.5
            )
        ),

        'E' to listOf(
            listOf(1.0 to 0.0, 0.0 to 0.0, 0.0 to 1.5, 1.0 to 1.5),
            listOf(0.0 to 0.75, 0.7 to 0.75)
        ),

        'F' to listOf(
            listOf(1.0 to 0.0, 0.0 to 0.0, 0.0 to 1.5),
            listOf(0.0 to 0.75, 0.7 to 0.75)
        ),

        'G' to listOf(
            listOf(
                1.0 to 0.3, 0.7 to 0.0, 0.3 to 0.0, 0.0 to 0.3,
                0.0 to 1.2, 0.3 to 1.5, 0.7 to 1.5, 1.0 to 1.2,
                1.0 to 0.75, 0.5 to 0.75
            )
        ),

        'H' to listOf(
            listOf(0.0 to 0.0, 0.0 to 1.5),
            listOf(1.0 to 0.0, 1.0 to 1.5),
            listOf(0.0 to 0.75, 1.0 to 0.75)
        ),

        'I' to listOf(
            listOf(0.3 to 0.0, 0.7 to 0.0),
            listOf(0.5 to 0.0, 0.5 to 1.5),
            listOf(0.3 to 1.5, 0.7 to 1.5)
        ),

        'J' to listOf(
            listOf(
                0.3 to 0.0, 0.7 to 0.0,
                0.7 to 1.2, 0.5 to 1.5, 0.2 to 1.5, 0.0 to 1.2
            )
        ),

        'K' to listOf(
            listOf(0.0 to 0.0, 0.0 to 1.5),
            listOf(1.0 to 0.0, 0.0 to 0.75, 1.0 to 1.5)
        ),

        'L' to listOf(
            listOf(0.0 to 0.0, 0.0 to 1.5, 1.0 to 1.5)
        ),

        'M' to listOf(
            listOf(0.0 to 1.5, 0.0 to 0.0, 0.5 to 0.75, 1.0 to 0.0, 1.0 to 1.5)
        ),

        'N' to listOf(
            listOf(0.0 to 1.5, 0.0 to 0.0, 1.0 to 1.5, 1.0 to 0.0)
        ),

        'O' to listOf(
            listOf(
                0.3 to 0.0, 0.7 to 0.0, 1.0 to 0.3, 1.0 to 1.2,
                0.7 to 1.5, 0.3 to 1.5, 0.0 to 1.2, 0.0 to 0.3,
                0.3 to 0.0
            )
        ),

        'P' to listOf(
            listOf(
                0.0 to 1.5, 0.0 to 0.0,
                0.7 to 0.0, 0.9 to 0.15, 0.9 to 0.6, 0.7 to 0.75,
                0.0 to 0.75
            )
        ),

        'Q' to listOf(
            listOf(
                0.3 to 0.0, 0.7 to 0.0, 1.0 to 0.3, 1.0 to 1.2,
                0.7 to 1.5, 0.3 to 1.5, 0.0 to 1.2, 0.0 to 0.3,
                0.3 to 0.0
            ),
            listOf(0.7 to 1.1, 1.0 to 1.5)
        ),

        'R' to listOf(
            listOf(
                0.0 to 1.5, 0.0 to 0.0,
                0.7 to 0.0, 0.9 to 0.15, 0.9 to 0.6, 0.7 to 0.75,
                0.0 to 0.75
            ),
            listOf(0.5 to 0.75, 1.0 to 1.5)
        ),

        'S' to listOf(
            listOf(
                1.0 to 0.2, 0.8 to 0.0, 0.2 to 0.0, 0.0 to 0.2,
                0.0 to 0.55, 0.2 to 0.75, 0.8 to 0.75,
                1.0 to 0.95, 1.0 to 1.3, 0.8 to 1.5, 0.2 to 1.5, 0.0 to 1.3
            )
        ),

        'T' to listOf(
            listOf(0.0 to 0.0, 1.0 to 0.0),
            listOf(0.5 to 0.0, 0.5 to 1.5)
        ),

        'U' to listOf(
            listOf(
                0.0 to 0.0, 0.0 to 1.2, 0.3 to 1.5, 0.7 to 1.5, 1.0 to 1.2, 1.0 to 0.0
            )
        ),

        'V' to listOf(
            listOf(0.0 to 0.0, 0.5 to 1.5, 1.0 to 0.0)
        ),

        'W' to listOf(
            listOf(0.0 to 0.0, 0.25 to 1.5, 0.5 to 0.75, 0.75 to 1.5, 1.0 to 0.0)
        ),

        'X' to listOf(
            listOf(0.0 to 0.0, 1.0 to 1.5),
            listOf(1.0 to 0.0, 0.0 to 1.5)
        ),

        'Y' to listOf(
            listOf(0.0 to 0.0, 0.5 to 0.75, 1.0 to 0.0),
            listOf(0.5 to 0.75, 0.5 to 1.5)
        ),

        'Z' to listOf(
            listOf(0.0 to 0.0, 1.0 to 0.0, 0.0 to 1.5, 1.0 to 1.5)
        ),

        // ─── DIGITS ────────────────────────────────────────────────────

        '0' to listOf(
            listOf(
                0.3 to 0.0, 0.7 to 0.0, 1.0 to 0.3, 1.0 to 1.2,
                0.7 to 1.5, 0.3 to 1.5, 0.0 to 1.2, 0.0 to 0.3,
                0.3 to 0.0
            ),
            listOf(0.2 to 1.2, 0.8 to 0.3) // Diagonal slash
        ),

        '1' to listOf(
            listOf(0.3 to 0.3, 0.5 to 0.0, 0.5 to 1.5),
            listOf(0.2 to 1.5, 0.8 to 1.5)
        ),

        '2' to listOf(
            listOf(
                0.0 to 0.3, 0.2 to 0.0, 0.8 to 0.0, 1.0 to 0.3,
                1.0 to 0.6, 0.0 to 1.5, 1.0 to 1.5
            )
        ),

        '3' to listOf(
            listOf(
                0.0 to 0.2, 0.2 to 0.0, 0.8 to 0.0, 1.0 to 0.2,
                1.0 to 0.55, 0.8 to 0.75, 0.5 to 0.75,
                0.8 to 0.75, 1.0 to 0.95, 1.0 to 1.3,
                0.8 to 1.5, 0.2 to 1.5, 0.0 to 1.3
            )
        ),

        '4' to listOf(
            listOf(0.7 to 1.5, 0.7 to 0.0, 0.0 to 1.0, 1.0 to 1.0)
        ),

        '5' to listOf(
            listOf(
                1.0 to 0.0, 0.0 to 0.0, 0.0 to 0.75,
                0.7 to 0.75, 1.0 to 0.95, 1.0 to 1.3,
                0.8 to 1.5, 0.2 to 1.5, 0.0 to 1.3
            )
        ),

        '6' to listOf(
            listOf(
                0.8 to 0.0, 0.3 to 0.0, 0.0 to 0.3,
                0.0 to 1.2, 0.3 to 1.5, 0.7 to 1.5, 1.0 to 1.2,
                1.0 to 0.95, 0.7 to 0.75, 0.0 to 0.75
            )
        ),

        '7' to listOf(
            listOf(0.0 to 0.0, 1.0 to 0.0, 0.3 to 1.5)
        ),

        '8' to listOf(
            listOf(
                0.5 to 0.75, 0.2 to 0.75, 0.0 to 0.55, 0.0 to 0.2,
                0.2 to 0.0, 0.8 to 0.0, 1.0 to 0.2, 1.0 to 0.55,
                0.8 to 0.75, 0.2 to 0.75,
                0.0 to 0.95, 0.0 to 1.3, 0.2 to 1.5,
                0.8 to 1.5, 1.0 to 1.3, 1.0 to 0.95,
                0.8 to 0.75
            )
        ),

        '9' to listOf(
            listOf(
                1.0 to 0.75, 0.3 to 0.75, 0.0 to 0.55,
                0.0 to 0.2, 0.2 to 0.0, 0.8 to 0.0, 1.0 to 0.2,
                1.0 to 1.2, 0.7 to 1.5, 0.2 to 1.5
            )
        ),

        // ─── SPACE ─────────────────────────────────────────────────────
        ' ' to emptyList()
    )

    /**
     * Get the effective width of a character including special handling for space.
     */
    fun getCharWidth(char: Char): Double {
        return if (char == ' ') SPACE_WIDTH else CHAR_WIDTH
    }
}
