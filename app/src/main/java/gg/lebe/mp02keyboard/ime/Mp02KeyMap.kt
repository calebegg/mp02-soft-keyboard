package gg.lebe.mp02keyboard.ime

import android.graphics.Path
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

enum class Mp02KeyId {
    Q, W, E, R, T, Y, U, I, O, P,
    A, S, D, F, G, H, J, K, L, BACKSPACE,
    SHIFT, Z, X, C, V, B, N, M, PERIOD, ENTER,
    SYM_LEFT, CTRL, SEARCH, SPACE, MINIMAL, ALT, SYM_RIGHT
}

enum class Mp02KeyIcon {
    NONE,
    SHIFT,
    BACKSPACE,
    ENTER,
    SEARCH,
    MINIMAL_FIVE_DOT,
    ALT_SMILEY
}

enum class Mp02Layer {
    ALPHA,
    ALT_NUM
}

data class Mp02Rect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) * 0.5f
    val centerY: Float get() = (top + bottom) * 0.5f
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
    fun toRectF(): RectF = RectF(left, top, right, bottom)
}

data class Mp02KeySpec(
    val id: Mp02KeyId,
    val row: Int,
    val primaryLabel: String,
    val secondaryLabel: String? = null,
    val lowerChar: String? = null,
    val upperChar: String? = null,
    val altChar: String? = null,
    val icon: Mp02KeyIcon = Mp02KeyIcon.NONE,
    val widthMm: Float = Mp02Geometry.KEY_SIZE_MM,
    val hasHomingPip: Boolean = false
)

data class Mp02KeyBounds(
    val spec: Mp02KeySpec,
    val rect: Mp02Rect,
    val centerX: Float,
    val centerY: Float,
    val widthPx: Float,
    val heightPx: Float,
    val squirclePath: Path
)

data class Mp02DeckMetrics(
    val widthPx: Int,
    val heightPx: Int,
    val keyUnitMm: Float,
    val keySizePx: Float,
    val gapXPx: Float,
    val gapYPx: Float,
    val topBezelPx: Float,
    val bottomChinPx: Float,
    val keys: List<Mp02KeyBounds>
)

object Mp02KeyMap {
    val ROW_0 = listOf(
        Mp02KeySpec(Mp02KeyId.Q, 0, "Q", "#", "q", "Q", "#"),
        Mp02KeySpec(Mp02KeyId.W, 0, "W", "1", "w", "W", "1"),
        Mp02KeySpec(Mp02KeyId.E, 0, "E", "2", "e", "E", "2"),
        Mp02KeySpec(Mp02KeyId.R, 0, "R", "3", "r", "R", "3"),
        Mp02KeySpec(Mp02KeyId.T, 0, "T", "(", "t", "T", "("),
        Mp02KeySpec(Mp02KeyId.Y, 0, "Y", ")", "y", "Y", ")"),
        Mp02KeySpec(Mp02KeyId.U, 0, "U", "_", "u", "U", "_"),
        Mp02KeySpec(Mp02KeyId.I, 0, "I", "-", "i", "I", "-"),
        Mp02KeySpec(Mp02KeyId.O, 0, "O", "+", "o", "O", "+"),
        Mp02KeySpec(Mp02KeyId.P, 0, "P", "@", "p", "P", "@")
    )

    val ROW_1 = listOf(
        Mp02KeySpec(Mp02KeyId.A, 1, "A", "*", "a", "A", "*"),
        Mp02KeySpec(Mp02KeyId.S, 1, "S", "4", "s", "S", "4"),
        Mp02KeySpec(Mp02KeyId.D, 1, "D", "5", "d", "D", "5"),
        Mp02KeySpec(Mp02KeyId.F, 1, "F", "6", "f", "F", "6", hasHomingPip = true),
        Mp02KeySpec(Mp02KeyId.G, 1, "G", "/", "g", "G", "/"),
        Mp02KeySpec(Mp02KeyId.H, 1, "H", ":", "h", "H", ":"),
        Mp02KeySpec(Mp02KeyId.J, 1, "J", ";", "j", "J", ";", hasHomingPip = true),
        Mp02KeySpec(Mp02KeyId.K, 1, "K", "'", "k", "K", "'"),
        Mp02KeySpec(Mp02KeyId.L, 1, "L", "\"", "l", "L", "\""),
        Mp02KeySpec(Mp02KeyId.BACKSPACE, 1, "", null, icon = Mp02KeyIcon.BACKSPACE)
    )

    val ROW_2 = listOf(
        Mp02KeySpec(Mp02KeyId.SHIFT, 2, "", null, icon = Mp02KeyIcon.SHIFT),
        Mp02KeySpec(Mp02KeyId.Z, 2, "Z", "7", "z", "Z", "7"),
        Mp02KeySpec(Mp02KeyId.X, 2, "X", "8", "x", "X", "8"),
        Mp02KeySpec(Mp02KeyId.C, 2, "C", "9", "c", "C", "9"),
        Mp02KeySpec(Mp02KeyId.V, 2, "V", "%", "v", "V", "%"),
        Mp02KeySpec(Mp02KeyId.B, 2, "B", "?", "b", "B", "?"),
        Mp02KeySpec(Mp02KeyId.N, 2, "N", "!", "n", "N", "!"),
        Mp02KeySpec(Mp02KeyId.M, 2, "M", ",", "m", "M", ","),
        Mp02KeySpec(Mp02KeyId.PERIOD, 2, ".", "$", ".", ".", "$"),
        Mp02KeySpec(Mp02KeyId.ENTER, 2, "", null, icon = Mp02KeyIcon.ENTER)
    )

    val ROW_3 = listOf(
        Mp02KeySpec(Mp02KeyId.SYM_LEFT, 3, "123", null),
        Mp02KeySpec(Mp02KeyId.CTRL, 3, "ctrl", "0", altChar = "0"),
        Mp02KeySpec(Mp02KeyId.SEARCH, 3, "", null, icon = Mp02KeyIcon.SEARCH),
        Mp02KeySpec(
            Mp02KeyId.SPACE,
            3,
            "",
            null,
            lowerChar = " ",
            upperChar = " ",
            altChar = " ",
            widthMm = Mp02Geometry.SPACE_WIDTH_MM
        ),
        Mp02KeySpec(Mp02KeyId.MINIMAL, 3, "", null, icon = Mp02KeyIcon.MINIMAL_FIVE_DOT),
        Mp02KeySpec(Mp02KeyId.ALT, 3, "alt", null, icon = Mp02KeyIcon.ALT_SMILEY),
        Mp02KeySpec(Mp02KeyId.SYM_RIGHT, 3, "123", null)
    )

    val ROWS: List<List<Mp02KeySpec>> = listOf(ROW_0, ROW_1, ROW_2, ROW_3)
    val ALL_KEYS: List<Mp02KeySpec> = ROWS.flatten()
}

object Mp02Geometry {
    // Hardware Minimal Phone 2 (MP02) dimensions measured from orthogonal schematics (72.0 mm body width).
    const val KEY_SIZE_MM = 5.33f
    const val GAP_X_MM = 1.50f
    const val GAP_Y_MM = 1.50f
    const val SPACE_WIDTH_MM = 17.50f
    const val TOP_BEZEL_MM = 2.24f
    const val SIDE_MARGIN_ON_OLED_MM = 0.65f
    const val GRID_WIDTH_MM = 10f * KEY_SIZE_MM + 9f * GAP_X_MM // 66.80 mm
    const val SQUIRCLE_EXP = 2.95

    fun computeLayout(
        viewWidthPx: Int,
        pxPerMmX: Float,
        pxPerMmY: Float,
        heightScale: Float = 1.0f,
        bottomChinMm: Float = 2.2f
    ): Mp02DeckMetrics {
        val safeW = viewWidthPx.coerceAtLeast(320)
        val safePxMmX = pxPerMmX.coerceAtLeast(4f)
        val safePxMmY = pxPerMmY.coerceAtLeast(4f)

        val viewWidthMm = safeW / safePxMmX
        val usableWidthMm = (viewWidthMm - 2f * SIDE_MARGIN_ON_OLED_MM).coerceAtLeast(36f)
        val widthFitScale = (usableWidthMm / GRID_WIDTH_MM).coerceAtMost(1.05f)

        val keyWidthFactor = widthFitScale
        val keyHeightFactor = widthFitScale * heightScale

        val unitKeyWPx = KEY_SIZE_MM * keyWidthFactor * safePxMmX
        val unitKeyHPx = KEY_SIZE_MM * keyHeightFactor * safePxMmY
        val gapXPx = GAP_X_MM * keyWidthFactor * safePxMmX
        val gapYPx = GAP_Y_MM * keyHeightFactor * safePxMmY
        val topBezelPx = TOP_BEZEL_MM * keyHeightFactor * safePxMmY
        val bottomChinPx = bottomChinMm * safePxMmY

        val keyBounds = ArrayList<Mp02KeyBounds>(37)
        var curY = topBezelPx

        for (rowSpecs in Mp02KeyMap.ROWS) {
            val rowWidthPx = rowSpecs.sumOf { (it.widthMm * keyWidthFactor * safePxMmX).toDouble() }.toFloat() +
                (rowSpecs.size - 1) * gapXPx
            var curX = (safeW - rowWidthPx) * 0.5f

            for (spec in rowSpecs) {
                val kwPx = spec.widthMm * keyWidthFactor * safePxMmX
                val rect = Mp02Rect(curX, curY, curX + kwPx, curY + unitKeyHPx)
                keyBounds += Mp02KeyBounds(
                    spec = spec,
                    rect = rect,
                    centerX = rect.centerX,
                    centerY = rect.centerY,
                    widthPx = kwPx,
                    heightPx = unitKeyHPx,
                    squirclePath = buildSquirclePath(rect)
                )
                curX += kwPx + gapXPx
            }
            curY += unitKeyHPx + gapYPx
        }

        val totalHeightPx = (curY - gapYPx + bottomChinPx).toInt().coerceAtLeast(120)
        return Mp02DeckMetrics(
            widthPx = safeW,
            heightPx = totalHeightPx,
            keyUnitMm = KEY_SIZE_MM * keyWidthFactor,
            keySizePx = unitKeyWPx,
            gapXPx = gapXPx,
            gapYPx = gapYPx,
            topBezelPx = topBezelPx,
            bottomChinPx = bottomChinPx,
            keys = keyBounds
        )
    }

    /**
     * Generates a smooth continuous-curvature superellipse (squircle) matching MP02 physical keycaps.
     */
    fun buildSquirclePath(rect: Mp02Rect, segmentsPerCorner: Int = 14): Path {
        val path = Path()
        val w = rect.width
        val h = rect.height
        val rHalf = minOf(w, h) * 0.5f
        val straightW = (w * 0.5f - rHalf).coerceAtLeast(0f)
        val straightH = (h * 0.5f - rHalf).coerceAtLeast(0f)
        val cx = rect.centerX
        val cy = rect.centerY
        val invExp = 2.0 / SQUIRCLE_EXP

        val corners = listOf(
            Triple(0.0, Math.PI * 0.5, Pair(straightW, straightH)),
            Triple(Math.PI * 0.5, Math.PI, Pair(-straightW, straightH)),
            Triple(Math.PI, Math.PI * 1.5, Pair(-straightW, -straightH)),
            Triple(Math.PI * 1.5, Math.PI * 2.0, Pair(straightW, -straightH))
        )

        var started = false
        for ((startAngle, endAngle, offset) in corners) {
            val (ox, oy) = offset
            for (s in 0..segmentsPerCorner) {
                val t = s.toDouble() / segmentsPerCorner
                val theta = startAngle + (endAngle - startAngle) * t
                val ct = cos(theta)
                val st = sin(theta)
                val px = cx + ox + (sign(ct) * abs(ct).pow(invExp) * rHalf).toFloat()
                val py = cy + oy + (sign(st) * abs(st).pow(invExp) * rHalf).toFloat()
                if (!started) {
                    path.moveTo(px, py)
                    started = true
                } else {
                    path.lineTo(px, py)
                }
            }
        }
        path.close()
        return path
    }

    /**
     * Voronoi-like hit testing: snaps taps inside the 1.5mm inter-key gaps or edges to the closest keycap.
     */
    fun findHitKey(metrics: Mp02DeckMetrics, x: Float, y: Float): Mp02KeyBounds? {
        for (k in metrics.keys) {
            if (k.rect.contains(x, y)) return k
        }
        var best: Mp02KeyBounds? = null
        var bestDist = Float.MAX_VALUE
        val maxCapturePad = metrics.gapXPx * 1.1f + metrics.topBezelPx * 0.6f
        for (k in metrics.keys) {
            val dx = when {
                x < k.rect.left -> k.rect.left - x
                x > k.rect.right -> x - k.rect.right
                else -> 0f
            }
            val dy = when {
                y < k.rect.top -> k.rect.top - y
                y > k.rect.bottom -> y - k.rect.bottom
                else -> 0f
            }
            val dist = hypot(dx, dy)
            if (dist < bestDist && dist <= maxCapturePad) {
                bestDist = dist
                best = k
            }
        }
        return best
    }
}
