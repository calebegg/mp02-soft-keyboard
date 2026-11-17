package gg.lebe.mp02keyboard.ime

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.ResourcesCompat
import gg.lebe.mp02keyboard.R

class Mp02KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onCommitText(text: String)
        fun onBackspaceChar()
        fun onBackspaceWord()
        fun onEnterKey(shiftHeld: Boolean)
        fun onSearchKey()
        fun onCursorMove(delta: Int, select: Boolean)
        fun onCtrlChord(keyId: Mp02KeyId)
        fun onShowImePicker()
    }

    var listener: Listener? = null
    val prefs = Mp02Preferences(context)

    var hasSpecialImeAction: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    val activeLayer: Mp02Layer
        get() = controller.activeLayer

    private var metrics: Mp02DeckMetrics? = null
    private var theme: Mp02Palette = resolveCurrentTheme()

    private val regularTypeface: Typeface =
        ResourcesCompat.getFont(context, R.font.inter_regular) ?: Typeface.DEFAULT
    private val mediumTypeface: Typeface =
        ResourcesCompat.getFont(context, R.font.inter_medium) ?: Typeface.DEFAULT_BOLD

    private val deckPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keycapRenderer = Mp02KeycapRenderer(regularTypeface)
    private val handler = Handler(Looper.getMainLooper())

    private var overlayCells: List<OverlayCell> = emptyList()
    private var pressedOverlayIndex: Int = -1

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private val controller = Mp02TouchController(
        sink = object : Mp02InputSink {
            override fun onCommitText(text: String) { listener?.onCommitText(text) }
            override fun onBackspaceChar() { listener?.onBackspaceChar() }
            override fun onBackspaceWord() { listener?.onBackspaceWord() }
            override fun onEnterKey(shiftHeld: Boolean) { listener?.onEnterKey(shiftHeld) }
            override fun onSearchKey() { listener?.onSearchKey() }
            override fun onCursorMove(delta: Int, select: Boolean) { listener?.onCursorMove(delta, select) }
            override fun onCtrlChord(keyId: Mp02KeyId) { listener?.onCtrlChord(keyId) }
            override fun onShowImePicker() { listener?.onShowImePicker() }
            override fun onKeyHaptic() { performKeyHaptic() }
            override fun onTickHaptic() { performTickHaptic() }
            override fun onRedrawRequested() { invalidate() }
            override fun onOverlayChanged() { rebuildOverlayIfNeeded() }
        },
        scheduler = object : Mp02TimerScheduler {
            override fun postDelayed(runnable: Runnable, delayMs: Long) {
                handler.postDelayed(runnable, delayMs)
            }
            override fun cancel(runnable: Runnable) {
                handler.removeCallbacks(runnable)
            }
        }
    )

    fun resolveCurrentTheme(): Mp02Palette {
        val nightMask = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isSysDark = nightMask == Configuration.UI_MODE_NIGHT_YES
        return Mp02Themes.resolve(prefs.finish, isSysDark)
    }

    fun refreshConfigAndRedraw() {
        theme = resolveCurrentTheme()
        metrics = null
        keycapRenderer.invalidateCaches()
        requestLayout()
        invalidate()
    }

    fun getCurrentDeckBottomColor(): Int = resolveCurrentTheme().deckBottomColor
    fun isCurrentThemeLight(): Boolean = resolveCurrentTheme().isLight
    fun setAutoShift(shouldCap: Boolean) = controller.setAutoShift(shouldCap)
    fun setInputNumericHint(isNumeric: Boolean) = controller.setInputNumericHint(isNumeric)
    fun resetModifiersForNewField() = controller.resetModifiersForNewField()
    fun clearPointers() = controller.clearPointers()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val dm = resources.displayMetrics
        val specW = MeasureSpec.getSize(widthMeasureSpec)
        val width = if (specW > 0) specW else dm.widthPixels
        val pxMmX = if (dm.xdpi in 80f..1000f) dm.xdpi / 25.4f else dm.density * 6.3f
        val pxMmY = if (dm.ydpi in 80f..1000f) dm.ydpi / 25.4f else dm.density * 6.3f

        val computed = Mp02Geometry.computeLayout(
            viewWidthPx = width,
            pxPerMmX = pxMmX,
            pxPerMmY = pxMmY,
            heightScale = prefs.heightScale,
            bottomChinMm = prefs.bottomChinMm
        )
        metrics = computed
        theme = resolveCurrentTheme()
        keycapRenderer.invalidateCaches()
        rebuildOverlayIfNeeded()
        setMeasuredDimension(computed.widthPx, computed.heightPx)
    }

    private fun rebuildOverlayIfNeeded() {
        val m = metrics ?: return
        overlayCells = when (controller.deckMode) {
            Mp02DeckMode.KEYS -> emptyList()
            Mp02DeckMode.EMOJI -> Mp02OverlayBuilder.buildEmojiCells(m, controller.emojiCategoryIndex, prefs.recentEmojis)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val m = metrics ?: return
        val t = theme

        deckPaint.shader = LinearGradient(
            0f, 0f, 0f, m.heightPx.toFloat(),
            intArrayOf(t.deckTopColor, t.deckMidColor, t.deckBottomColor),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, m.widthPx.toFloat(), m.heightPx.toFloat(), deckPaint)
        deckPaint.shader = null

        deckPaint.color = t.topChamferDark
        canvas.drawRect(0f, 0f, m.widthPx.toFloat(), 1.6f, deckPaint)
        deckPaint.color = t.topChamferLight
        canvas.drawRect(0f, 1.6f, m.widthPx.toFloat(), 3.2f, deckPaint)

        if (controller.deckMode != Mp02DeckMode.KEYS) {
            Mp02OverlayBuilder.drawCells(canvas, overlayCells, pressedOverlayIndex, t, mediumTypeface, overlayPaint)
            return
        }

        val renderState = controller.buildRenderState(t, prefs.backlightEnabled, hasSpecialImeAction)
        for (key in m.keys) {
            keycapRenderer.drawKeyCap(canvas, key, controller.isKeyPressed(key.spec.id), renderState)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val m = metrics ?: return false
        if (controller.deckMode != Mp02DeckMode.KEYS) {
            return handleOverlayTouch(event)
        }

        val config = Mp02InputConfig(
            spacebarTrackpad = prefs.spacebarTrackpad,
            longPressForAlt = prefs.longPressForAlt,
            doubleSpacePeriod = prefs.doubleSpacePeriod
        )
        val actionIndex = event.actionIndex
        val pointerId = event.getPointerId(actionIndex)
        val now = System.currentTimeMillis()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                controller.onPointerDown(m, pointerId, event.getX(actionIndex), event.getY(actionIndex), now, config)
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    controller.onPointerMove(m, event.getPointerId(i), event.getX(i), event.getY(i), config)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                controller.onPointerUp(pointerId, now, config)
            }
            MotionEvent.ACTION_CANCEL -> {
                controller.onCancel()
            }
        }
        return true
    }

    private fun handleOverlayTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedOverlayIndex = overlayCells.indexOfFirst { it.rect.contains(event.x, event.y) }
                if (pressedOverlayIndex >= 0) performKeyHaptic()
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val idx = overlayCells.indexOfFirst { it.rect.contains(event.x, event.y) }
                if (idx != pressedOverlayIndex) {
                    pressedOverlayIndex = idx
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                val idx = pressedOverlayIndex
                pressedOverlayIndex = -1
                if (idx in overlayCells.indices) {
                    controller.activateOverlayCell(
                        cell = overlayCells[idx],
                        onRecordEmoji = { prefs.recordRecentEmoji(it) }
                    )
                } else {
                    invalidate()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedOverlayIndex = -1
                invalidate()
            }
        }
        return true
    }

    private fun performKeyHaptic() {
        if (!prefs.hapticsEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    private fun performTickHaptic() {
        if (!prefs.hapticsEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    override fun onDetachedFromWindow() {
        controller.clearPointers()
        super.onDetachedFromWindow()
    }
}
