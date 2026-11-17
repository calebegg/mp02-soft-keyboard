package gg.lebe.mp02keyboard.ime

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface

enum class Mp02DeckMode {
    KEYS,
    EMOJI
}

data class OverlayCell(
    val rect: Mp02Rect,
    val path: android.graphics.Path,
    val label: String,
    val isAccent: Boolean = false,
    val backToKeys: Boolean = false,
    val emojiValue: String? = null,
    val emojiCategoryIndex: Int? = null
)

object Mp02OverlayBuilder {
    private val EMOJI_CATEGORIES: List<Pair<String, List<String>>> = listOf(
        "REC" to emptyList(),
        "FACE" to listOf(
            "😊", "😂", "🥹", "😅", "🤣", "😉", "😌", "🤔",
            "🤨", "😐", "😶", "🙄", "😬", "😴", "🥳", "🤯",
            "😎", "🤓", "😕", "😮", "😢", "😭", "😤", "🤗"
        ),
        "HAND" to listOf(
            "👍", "👎", "👌", "✌️", "🤞", "🤟", "🙌", "👏",
            "🙏", "🤝", "💪", "🫶", "👋", "🫡", "☝️", "👇",
            "👈", "👉", "✋", "🤜", "🤛", "✍️", "👀", "🧠"
        ),
        "SIGN" to listOf(
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
            "🔥", "✨", "⚡", "🌟", "🎉", "🎊", "💯", "✅",
            "❌", "⚠️", "🚀", "💡", "📌", "🔍", "⏰", "📅"
        ),
        "LIFE" to listOf(
            "☕", "🍵", "🥗", "🥑", "🌮", "🍜", "🍞", "🌱",
            "🌿", "🌳", "🌧️", "☀️", "❄️", "🌙", "🐶", "🐾",
            "🎲", "🎭", "📚", "🎧", "🚲", "🚇", "🏠", "🛠️"
        )
    )

    fun buildEmojiCells(
        metrics: Mp02DeckMetrics,
        categoryIndex: Int,
        recentEmojis: List<String>
    ): List<OverlayCell> {
        val safeCat = categoryIndex.coerceIn(0, EMOJI_CATEGORIES.lastIndex)
        val emojis = if (safeCat == 0) recentEmojis else EMOJI_CATEGORIES[safeCat].second
        val cols = 8
        val emojiRows = 3
        val totalRows = 4

        val out = ArrayList<OverlayCell>(32)
        val marginX = metrics.keys.first().rect.left
        val usableW = metrics.widthPx - 2f * marginX
        val gapX = metrics.gapXPx * 0.85f
        val gapY = metrics.gapYPx * 0.85f
        val topStart = metrics.topBezelPx
        val totalGridH = metrics.heightPx - topStart - metrics.bottomChinPx
        val cellH = (totalGridH - gapY * (totalRows - 1)) / totalRows

        val tabs = mutableListOf<Pair<String, Int?>>()
        tabs += "KEYS" to null
        EMOJI_CATEGORIES.forEachIndexed { idx, (name, _) -> tabs += name to idx }
        val tabW = (usableW - gapX * (tabs.size - 1)) / tabs.size
        tabs.forEachIndexed { idx, (title, catIdx) ->
            val x = marginX + idx * (tabW + gapX)
            val rect = Mp02Rect(x, topStart, x + tabW, topStart + cellH * 0.86f)
            out += OverlayCell(
                rect = rect,
                path = Mp02Geometry.buildSquirclePath(rect),
                label = title,
                isAccent = (catIdx == safeCat) || (catIdx == null),
                backToKeys = (catIdx == null),
                emojiCategoryIndex = catIdx
            )
        }

        val cellW = (usableW - gapX * (cols - 1)) / cols
        val gridTop = topStart + cellH * 0.86f + gapY
        val remainingH = (metrics.heightPx - metrics.bottomChinPx - gridTop - gapY * (emojiRows - 1)) / emojiRows
        for (r in 0 until emojiRows) {
            for (c in 0 until cols) {
                val eIdx = r * cols + c
                if (eIdx >= emojis.size) break
                val x = marginX + c * (cellW + gapX)
                val y = gridTop + r * (remainingH + gapY)
                val rect = Mp02Rect(x, y, x + cellW, y + remainingH)
                out += OverlayCell(
                    rect = rect,
                    path = Mp02Geometry.buildSquirclePath(rect),
                    label = emojis[eIdx],
                    emojiValue = emojis[eIdx]
                )
            }
        }
        return out
    }

    fun drawCells(
        canvas: Canvas,
        cells: List<OverlayCell>,
        pressedIndex: Int,
        theme: Mp02Palette,
        mediumTypeface: Typeface,
        paint: Paint
    ) {
        cells.forEachIndexed { index, cell ->
            val pressed = index == pressedIndex
            val dy = if (pressed) cell.rect.height * 0.035f else 0f
            canvas.save()
            canvas.translate(0f, dy)

            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.color = theme.keyWellOuter
            canvas.drawPath(cell.path, paint)

            paint.color = when {
                pressed -> theme.keyPressedTop
                cell.isAccent -> theme.overlayCardBg
                else -> theme.keyFaceMid
            }
            canvas.save()
            canvas.translate(0f, -0.4f)
            canvas.drawPath(cell.path, paint)
            canvas.restore()

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f
            paint.color = if (cell.isAccent) theme.overlayCardBorder else theme.keyInnerHighlight
            canvas.drawPath(cell.path, paint)

            paint.style = Paint.Style.FILL
            paint.typeface = mediumTypeface
            paint.textAlign = Paint.Align.CENTER
            val isEmoji = cell.emojiValue != null
            paint.textSize = when {
                isEmoji -> cell.rect.height * 0.50f
                cell.label.length > 4 -> cell.rect.height * 0.23f
                else -> cell.rect.height * 0.28f
            }
            paint.color = when {
                cell.isAccent && !isEmoji -> theme.primaryLegendPressed
                pressed -> theme.primaryLegendPressed
                else -> theme.primaryLegend
            }
            val fm = paint.fontMetrics
            val baseline = cell.rect.centerY - (fm.ascent + fm.descent) * 0.5f
            canvas.drawText(cell.label, cell.rect.centerX, baseline, paint)

            canvas.restore()
            paint.shader = null
        }
    }
}
