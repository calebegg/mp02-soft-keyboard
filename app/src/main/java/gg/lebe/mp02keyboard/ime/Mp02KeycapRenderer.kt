package gg.lebe.mp02keyboard.ime

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

data class Mp02RenderState(
    val theme: Mp02Palette,
    val shiftState: ShiftState,
    val shiftActive: Boolean,
    val altActive: Boolean,
    val altLocked: Boolean,
    val ctrlActive: Boolean,
    val activeLayer: Mp02Layer,
    val backlightEnabled: Boolean,
    val hasSpecialImeAction: Boolean
)

class Mp02KeycapRenderer(
    private val regularTypeface: Typeface
) {
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var cachedTheme: Mp02Palette? = null
    private val rowGradNormal = HashMap<Int, LinearGradient>(4)
    private val rowGradPressed = HashMap<Int, LinearGradient>(4)
    private var cachedGlowWidth = -1f
    private var cachedGlowTheme: Mp02Palette? = null
    private var cachedGlowShader: RadialGradient? = null
    private val glowMatrix = Matrix()
    private val smileRect = RectF()

    private var cachedIconWidth = -1f
    private val shiftIconPath = Path()
    private val bkspIconPath = Path()
    private val enterIconPath = Path()

    private fun ensureIconPaths(w: Float) {
        if (cachedIconWidth == w) return
        cachedIconWidth = w

        val s = w * 0.19f
        shiftIconPath.apply {
            reset()
            moveTo(0f, -s * 1.05f)
            lineTo(s * 1.08f, s * 0.05f)
            lineTo(s * 0.45f, s * 0.05f)
            lineTo(s * 0.45f, s * 0.95f)
            lineTo(-s * 0.45f, s * 0.95f)
            lineTo(-s * 0.45f, s * 0.05f)
            lineTo(-s * 1.08f, s * 0.05f)
            close()
        }

        val hwB = w * 0.215f
        val hhB = w * 0.135f
        val shoulderX = -hwB * 0.35f
        bkspIconPath.apply {
            reset()
            moveTo(-hwB, 0f)
            lineTo(shoulderX, -hhB)
            lineTo(hwB - hhB * 0.28f, -hhB)
            quadTo(hwB, -hhB, hwB, -hhB * 0.72f)
            lineTo(hwB, hhB * 0.72f)
            quadTo(hwB, hhB, hwB - hhB * 0.28f, hhB)
            lineTo(shoulderX, hhB)
            close()
        }

        val hwE = w * 0.19f
        val hhE = w * 0.115f
        val ah = hhE * 0.65f
        enterIconPath.apply {
            reset()
            moveTo(hwE, -hhE * 1.05f)
            lineTo(hwE, hhE * 0.25f)
            quadTo(hwE, hhE * 0.65f, hwE * 0.55f, hhE * 0.65f)
            lineTo(-hwE, hhE * 0.65f)
            moveTo(-hwE + ah, hhE * 0.65f - ah)
            lineTo(-hwE, hhE * 0.65f)
            lineTo(-hwE + ah, hhE * 0.65f + ah)
        }
    }

    private fun getRowShader(key: Mp02KeyBounds, pressed: Boolean, theme: Mp02Palette): LinearGradient {
        if (cachedTheme !== theme) {
            rowGradNormal.clear()
            rowGradPressed.clear()
            cachedTheme = theme
        }
        val map = if (pressed) rowGradPressed else rowGradNormal
        val row = key.spec.row
        return map.getOrPut(row) {
            val topColor = if (pressed) theme.keyPressedTop else theme.keyFaceTop
            val midColor = if (pressed) theme.keyPressedTop else theme.keyFaceMid
            val botColor = if (pressed) theme.keyPressedBottom else theme.keyFaceBottom
            LinearGradient(
                0f, key.rect.top, 0f, key.rect.bottom,
                intArrayOf(topColor, midColor, botColor),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
    }

    private fun getGlowShader(key: Mp02KeyBounds, theme: Mp02Palette): RadialGradient {
        val cached = cachedGlowShader
        if (cached != null && cachedGlowTheme === theme && cachedGlowWidth == key.widthPx) {
            return cached
        }
        val shader = RadialGradient(
            0f, 0f, (key.widthPx * 0.55f).coerceAtLeast(1f),
            theme.backlightGlow, 0x00FFFFFF,
            Shader.TileMode.CLAMP
        )
        cachedGlowTheme = theme
        cachedGlowWidth = key.widthPx
        cachedGlowShader = shader
        return shader
    }

    fun invalidateCaches() {
        rowGradNormal.clear()
        rowGradPressed.clear()
        cachedTheme = null
        cachedGlowTheme = null
        cachedGlowWidth = -1f
        cachedGlowShader = null
        cachedIconWidth = -1f
    }

    fun drawKeyCap(canvas: Canvas, key: Mp02KeyBounds, pressed: Boolean, state: Mp02RenderState) {
        val t = state.theme
        val travel = if (pressed) key.heightPx * 0.036f else 0f
        val shadowDrop = if (pressed) key.heightPx * 0.014f else key.heightPx * 0.042f

        // 1. Drop shadow below recessed keycap
        canvas.save()
        canvas.translate(0f, shadowDrop + travel)
        capPaint.shader = null
        capPaint.style = Paint.Style.FILL
        capPaint.color = t.keyDropShadow
        canvas.drawPath(key.squirclePath, capPaint)
        canvas.restore()

        // 2. Recessed outer cavity seam
        canvas.save()
        canvas.translate(0f, travel)
        rimPaint.strokeWidth = (key.heightPx * 0.022f).coerceAtLeast(1.8f)
        rimPaint.color = t.keyWellOuter
        canvas.drawPath(key.squirclePath, rimPaint)

        // 3. Sculpted polycarbonate/silicone dome fill
        capPaint.style = Paint.Style.FILL
        capPaint.shader = getRowShader(key, pressed, t)
        canvas.drawPath(key.squirclePath, capPaint)
        capPaint.shader = null

        // 4. Inner bevel catchlight (top specular curve & bottom lip) clipped cleanly to squircle
        canvas.save()
        canvas.clipPath(key.squirclePath)
        canvas.translate(0f, key.heightPx * 0.022f)
        rimPaint.strokeWidth = (key.heightPx * 0.024f).coerceAtLeast(1.6f)
        rimPaint.color = t.keyInnerHighlight
        canvas.drawPath(key.squirclePath, rimPaint)
        canvas.translate(0f, -key.heightPx * 0.044f)
        rimPaint.color = t.keyInnerBottomLip
        canvas.drawPath(key.squirclePath, rimPaint)
        canvas.restore()

        // 5. Backlight flare when pressed
        if (state.backlightEnabled && pressed) {
            val shader = getGlowShader(key, t)
            glowMatrix.setTranslate(key.centerX, key.centerY)
            shader.setLocalMatrix(glowMatrix)
            capPaint.shader = shader
            canvas.drawPath(key.squirclePath, capPaint)
            capPaint.shader = null
        }

        // 6. Legends, homing pips, icons, and active state micro-LEDs
        drawKeyLegends(canvas, key, pressed, state)
        canvas.restore()
    }

    private fun drawKeyLegends(canvas: Canvas, key: Mp02KeyBounds, pressed: Boolean, state: Mp02RenderState) {
        val t = state.theme
        val spec = key.spec
        val rect = key.rect
        val w = key.widthPx
        val h = key.heightPx

        val primaryColor = if (pressed) t.primaryLegendPressed else t.primaryLegend
        val secondaryColor = if (pressed) t.secondaryLegendPressed else t.secondaryLegend

        when (spec.icon) {
            Mp02KeyIcon.SHIFT -> {
                val activeColor = when {
                    state.shiftState == ShiftState.CAPS_LOCK -> t.accentRed
                    state.shiftActive -> t.primaryLegendPressed
                    else -> primaryColor
                }
                drawShiftIcon(canvas, key.centerX, key.centerY, w, activeColor, filled = state.shiftState == ShiftState.CAPS_LOCK)
                if (state.shiftActive) drawMicroLed(canvas, rect, t.accentRed)
                return
            }
            Mp02KeyIcon.BACKSPACE -> {
                drawBackspaceIcon(canvas, key.centerX, key.centerY, w, primaryColor)
                return
            }
            Mp02KeyIcon.ENTER -> {
                val enterCol = if (state.hasSpecialImeAction) t.primaryLegendPressed else primaryColor
                drawEnterIcon(canvas, key.centerX, key.centerY, w, enterCol)
                if (state.hasSpecialImeAction) drawMicroLed(canvas, rect, t.accentRed)
                return
            }
            Mp02KeyIcon.SEARCH -> {
                drawSearchIcon(canvas, key.centerX, key.centerY, w, primaryColor)
                return
            }
            Mp02KeyIcon.MINIMAL_FIVE_DOT -> {
                drawFiveDotIcon(canvas, key.centerX, key.centerY, w, h, primaryColor)
                return
            }
            Mp02KeyIcon.ALT_SMILEY -> {
                val smileyColor = if (state.altActive) t.primaryLegendPressed else secondaryColor
                drawSmileyIcon(canvas, rect.left + w * 0.28f, rect.top + h * 0.27f, w * 0.115f, smileyColor)
                textPaint.typeface = regularTypeface
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.textSize = h * 0.28f
                textPaint.color = if (state.altActive) t.primaryLegendPressed else primaryColor
                canvas.drawText("alt", key.centerX, rect.top + h * 0.78f, textPaint)
                if (state.altActive) drawMicroLed(canvas, rect, t.accentRed)
                return
            }
            Mp02KeyIcon.NONE -> Unit
        }

        if (spec.id == Mp02KeyId.SPACE) return

        if (spec.id == Mp02KeyId.SYM_LEFT || spec.id == Mp02KeyId.SYM_RIGHT) {
            val symActive = state.activeLayer == Mp02Layer.ALT_NUM
            textPaint.typeface = regularTypeface
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = h * 0.265f
            textPaint.color = if (symActive) t.primaryLegendPressed else primaryColor
            val fm = textPaint.fontMetrics
            val baseline = key.centerY - (fm.ascent + fm.descent) * 0.5f
            canvas.drawText("123", key.centerX, baseline, textPaint)
            if (state.altLocked) drawMicroLed(canvas, rect, t.accentRed)
            return
        }

        if (spec.id == Mp02KeyId.CTRL) {
            textPaint.typeface = regularTypeface
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = h * 0.25f
            textPaint.color = when {
                state.altActive -> t.primaryLegendPressed
                state.ctrlActive -> t.dimLegend
                else -> secondaryColor
            }
            canvas.drawText("0", rect.left + w * 0.24f, rect.top + h * 0.34f, textPaint)

            textPaint.textSize = h * 0.27f
            textPaint.color = if (state.ctrlActive) t.primaryLegendPressed else if (state.altActive) t.dimLegend else primaryColor
            canvas.drawText("ctrl", key.centerX, rect.top + h * 0.77f, textPaint)
            if (state.ctrlActive) drawMicroLed(canvas, rect, t.accentRed)
            return
        }

        spec.secondaryLabel?.let { sec ->
            textPaint.typeface = regularTypeface
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = h * 0.255f
            textPaint.color = if (state.altActive) t.primaryLegendPressed else secondaryColor
            canvas.drawText(sec, rect.left + w * 0.24f, rect.top + h * 0.34f, textPaint)
        }

        if (spec.hasHomingPip) {
            drawHomingPip(canvas, key.centerX, rect.top + h * 0.24f, w * 0.044f, t)
        }

        val mainColor = if (state.altActive) t.dimLegend else primaryColor
        if (spec.id == Mp02KeyId.PERIOD) {
            capPaint.style = Paint.Style.FILL
            capPaint.shader = null
            capPaint.color = mainColor
            canvas.drawCircle(key.centerX, rect.top + h * 0.72f, w * 0.056f, capPaint)
        } else {
            textPaint.typeface = regularTypeface
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = h * 0.465f
            textPaint.color = mainColor
            canvas.drawText(spec.primaryLabel, key.centerX, rect.top + h * 0.81f, textPaint)
        }
    }

    private fun drawHomingPip(canvas: Canvas, cx: Float, cy: Float, r: Float, t: Mp02Palette) {
        capPaint.shader = null
        capPaint.style = Paint.Style.FILL
        capPaint.color = t.homingPipShadow
        canvas.drawCircle(cx + r * 0.28f, cy + r * 0.32f, r * 1.05f, capPaint)
        capPaint.color = t.homingPipBase
        canvas.drawCircle(cx, cy, r, capPaint)
        capPaint.color = t.homingPipHighlight
        canvas.drawCircle(cx - r * 0.28f, cy - r * 0.28f, r * 0.42f, capPaint)
    }

    private fun drawMicroLed(canvas: Canvas, rect: Mp02Rect, color: Int) {
        capPaint.shader = null
        capPaint.style = Paint.Style.FILL
        capPaint.color = color
        val r = rect.height * 0.045f
        canvas.drawCircle(rect.right - rect.width * 0.18f, rect.top + rect.height * 0.18f, r, capPaint)
    }

    private fun drawShiftIcon(canvas: Canvas, cx: Float, cy: Float, w: Float, color: Int, filled: Boolean) {
        ensureIconPaths(w)
        iconPaint.color = color
        iconPaint.strokeWidth = (w * 0.042f).coerceAtLeast(2.0f)
        iconPaint.style = if (filled) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
        canvas.save()
        canvas.translate(cx, cy)
        canvas.drawPath(shiftIconPath, iconPaint)
        canvas.restore()
        iconPaint.style = Paint.Style.STROKE
    }

    private fun drawBackspaceIcon(canvas: Canvas, cx: Float, cy: Float, w: Float, color: Int) {
        ensureIconPaths(w)
        iconPaint.color = color
        iconPaint.strokeWidth = (w * 0.036f).coerceAtLeast(1.8f)
        iconPaint.style = Paint.Style.STROKE
        canvas.save()
        canvas.translate(cx, cy)
        canvas.drawPath(bkspIconPath, iconPaint)

        val xCx = w * 0.215f * 0.22f
        val xr = w * 0.135f * 0.42f
        canvas.drawLine(xCx - xr, -xr, xCx + xr, xr, iconPaint)
        canvas.drawLine(xCx - xr, xr, xCx + xr, -xr, iconPaint)
        canvas.restore()
    }

    private fun drawEnterIcon(canvas: Canvas, cx: Float, cy: Float, w: Float, color: Int) {
        ensureIconPaths(w)
        iconPaint.color = color
        iconPaint.strokeWidth = (w * 0.042f).coerceAtLeast(2.0f)
        iconPaint.style = Paint.Style.STROKE
        canvas.save()
        canvas.translate(cx, cy)
        canvas.drawPath(enterIconPath, iconPaint)
        canvas.restore()
    }

    private fun drawSearchIcon(canvas: Canvas, cx: Float, cy: Float, w: Float, color: Int) {
        val r = w * 0.13f
        val ocx = cx - w * 0.03f
        val ocy = cy - w * 0.03f
        iconPaint.color = color
        iconPaint.strokeWidth = (w * 0.046f).coerceAtLeast(2.2f)
        iconPaint.style = Paint.Style.STROKE
        canvas.drawCircle(ocx, ocy, r, iconPaint)
        canvas.drawLine(ocx + r * 0.72f, ocy + r * 0.72f, cx + w * 0.18f, cy + w * 0.18f, iconPaint)
    }

    private fun drawFiveDotIcon(canvas: Canvas, cx: Float, cy: Float, w: Float, h: Float, color: Int) {
        capPaint.shader = null
        capPaint.style = Paint.Style.FILL
        capPaint.color = color
        val dx = w * 0.165f
        val dy = h * 0.105f
        val outerR = w * 0.058f
        val centerR = w * 0.044f
        canvas.drawCircle(cx - dx, cy - dy, outerR, capPaint)
        canvas.drawCircle(cx - dx, cy + dy, outerR, capPaint)
        canvas.drawCircle(cx, cy, centerR, capPaint)
        canvas.drawCircle(cx + dx, cy - dy, outerR, capPaint)
        canvas.drawCircle(cx + dx, cy + dy, outerR, capPaint)
    }

    private fun drawSmileyIcon(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        iconPaint.color = color
        iconPaint.strokeWidth = (r * 0.22f).coerceAtLeast(1.5f)
        iconPaint.style = Paint.Style.STROKE
        canvas.drawCircle(cx, cy, r, iconPaint)

        capPaint.shader = null
        capPaint.style = Paint.Style.FILL
        capPaint.color = color
        val eyeR = (r * 0.14f).coerceAtLeast(1.2f)
        canvas.drawCircle(cx - r * 0.34f, cy - r * 0.20f, eyeR, capPaint)
        canvas.drawCircle(cx + r * 0.34f, cy - r * 0.20f, eyeR, capPaint)

        smileRect.set(cx - r * 0.48f, cy - r * 0.25f, cx + r * 0.48f, cy + r * 0.52f)
        canvas.drawArc(smileRect, 20f, 140f, false, iconPaint)
    }
}
