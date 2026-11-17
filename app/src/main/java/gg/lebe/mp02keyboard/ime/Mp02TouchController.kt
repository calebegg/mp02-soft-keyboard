package gg.lebe.mp02keyboard.ime

import kotlin.math.abs
import kotlin.math.hypot

enum class ShiftState {
    OFF,
    ONE_SHOT,
    CAPS_LOCK
}

enum class LayerLockState {
    OFF,
    ONE_SHOT,
    LOCKED
}

interface Mp02TimerScheduler {
    fun postDelayed(runnable: Runnable, delayMs: Long)
    fun cancel(runnable: Runnable)
}

interface Mp02InputSink {
    fun onCommitText(text: String)
    fun onBackspaceChar()
    fun onBackspaceWord()
    fun onEnterKey(shiftHeld: Boolean)
    fun onSearchKey()
    fun onCursorMove(delta: Int, select: Boolean)
    fun onCtrlChord(keyId: Mp02KeyId)
    fun onCommandAction(action: Mp02CommandAction)
    fun onShowImePicker()
    fun onKeyHaptic()
    fun onTickHaptic()
    fun onRedrawRequested()
    fun onOverlayChanged()
}

data class Mp02InputConfig(
    val spacebarTrackpad: Boolean = true,
    val longPressForAlt: Boolean = true,
    val doubleSpacePeriod: Boolean = true
)

class Mp02TouchController(
    private val sink: Mp02InputSink,
    private val scheduler: Mp02TimerScheduler
) {
    var shiftState: ShiftState = ShiftState.OFF
        private set
    var altState: LayerLockState = LayerLockState.OFF
        private set
    var ctrlOneShot: Boolean = false
        private set
    var activeLayer: Mp02Layer = Mp02Layer.ALPHA
        private set
    var deckMode: Mp02DeckMode = Mp02DeckMode.KEYS
        private set
    var emojiCategoryIndex: Int = 1
        private set

    private data class ActivePointer(
        val pointerId: Int,
        val key: Mp02KeyBounds,
        val downX: Float,
        val downY: Float,
        var lastTrackpadX: Float = downX,
        var trackpadCarryPx: Float = 0f,
        var isTrackpadDragging: Boolean = false,
        var chordUsed: Boolean = false,
        var alreadyCommitted: Boolean = false,
        var longPressTriggered: Boolean = false,
        var swipedWordDelete: Boolean = false
    )

    private val activePointers = LinkedHashMap<Int, ActivePointer>()
    private val pointerRunnables = HashMap<Int, Runnable>()

    private var lastShiftTapMs = 0L
    private var lastAltTapMs = 0L
    private var lastSpaceTapMs = 0L
    private var lastCommittedChar: Char? = null

    fun isKeyPressed(keyId: Mp02KeyId): Boolean =
        activePointers.values.any { it.key.spec.id == keyId }

    fun setAutoShift(shouldCap: Boolean) {
        if (shiftState == ShiftState.CAPS_LOCK) return
        val desired = if (shouldCap) ShiftState.ONE_SHOT else ShiftState.OFF
        if (shiftState != desired) {
            shiftState = desired
            sink.onRedrawRequested()
        }
    }

    fun setInputNumericHint(isNumeric: Boolean) {
        if (isNumeric && activeLayer == Mp02Layer.ALPHA) {
            activeLayer = Mp02Layer.ALT_NUM
            altState = LayerLockState.LOCKED
            sink.onRedrawRequested()
        } else if (!isNumeric && altState == LayerLockState.LOCKED) {
            activeLayer = Mp02Layer.ALPHA
            altState = LayerLockState.OFF
            sink.onRedrawRequested()
        }
    }

    fun resetModifiersForNewField() {
        clearPointers()
        deckMode = Mp02DeckMode.KEYS
        activeLayer = Mp02Layer.ALPHA
        altState = LayerLockState.OFF
        ctrlOneShot = false
        shiftState = ShiftState.OFF
        lastCommittedChar = null
        sink.onOverlayChanged()
        sink.onRedrawRequested()
    }

    fun buildRenderState(theme: Mp02Palette, backlightEnabled: Boolean, hasSpecialImeAction: Boolean): Mp02RenderState {
        return Mp02RenderState(
            theme = theme,
            shiftState = shiftState,
            shiftActive = isKeyPressed(Mp02KeyId.SHIFT) || shiftState != ShiftState.OFF,
            altActive = isKeyPressed(Mp02KeyId.ALT) || activeLayer == Mp02Layer.ALT_NUM,
            altLocked = altState == LayerLockState.LOCKED,
            ctrlActive = isKeyPressed(Mp02KeyId.CTRL) || ctrlOneShot,
            activeLayer = activeLayer,
            backlightEnabled = backlightEnabled,
            hasSpecialImeAction = hasSpecialImeAction
        )
    }

    fun onPointerDown(
        metrics: Mp02DeckMetrics,
        pointerId: Int,
        x: Float,
        y: Float,
        nowMs: Long,
        config: Mp02InputConfig
    ) {
        val hit = Mp02Geometry.findHitKey(metrics, x, y) ?: return

        if (!isModifierKey(hit.spec.id)) {
            commitPendingRolloverKeys(nowMs, config)
            markHeldModifiersChordUsed()
        }

        val ptr = ActivePointer(pointerId = pointerId, key = hit, downX = x, downY = y)
        activePointers[pointerId] = ptr
        sink.onKeyHaptic()

        when (hit.spec.id) {
            Mp02KeyId.BACKSPACE -> {
                lastCommittedChar = null
                lastSpaceTapMs = 0L
                if (isKeyPressed(Mp02KeyId.CTRL) || ctrlOneShot) {
                    ctrlOneShot = false
                    sink.onBackspaceWord()
                } else {
                    sink.onBackspaceChar()
                    scheduleBackspaceRepeat(pointerId)
                }
                ptr.alreadyCommitted = true
            }
            Mp02KeyId.SHIFT -> scheduleShiftLongPress(pointerId)
            Mp02KeyId.ALT -> scheduleAltLongPress(pointerId)
            Mp02KeyId.SPACE -> scheduleSpaceLongPress(pointerId)
            else -> {
                if (config.longPressForAlt && !isModifierKey(hit.spec.id) && !isNavKey(hit.spec.id)) {
                    scheduleCharLongPress(pointerId)
                }
            }
        }
        sink.onRedrawRequested()
    }

    fun onPointerMove(metrics: Mp02DeckMetrics, pointerId: Int, x: Float, y: Float, config: Mp02InputConfig) {
        val ptr = activePointers[pointerId] ?: return
        if (ptr.key.spec.id == Mp02KeyId.SPACE && config.spacebarTrackpad && !ptr.longPressTriggered) {
            val totalDx = x - ptr.downX
            val slopPx = metrics.keySizePx * 0.42f
            if (!ptr.isTrackpadDragging && abs(totalDx) > slopPx) {
                ptr.isTrackpadDragging = true
                ptr.lastTrackpadX = ptr.downX + kotlin.math.sign(totalDx) * slopPx
                cancelRunnable(pointerId)
            }
            if (ptr.isTrackpadDragging) {
                val stepPx = metrics.keySizePx * 0.45f
                ptr.trackpadCarryPx += (x - ptr.lastTrackpadX)
                ptr.lastTrackpadX = x
                val steps = (ptr.trackpadCarryPx / stepPx).toInt()
                if (steps != 0) {
                    ptr.trackpadCarryPx -= steps * stepPx
                    val select = isKeyPressed(Mp02KeyId.SHIFT) || shiftState != ShiftState.OFF
                    lastCommittedChar = null
                    sink.onCursorMove(steps, select)
                    sink.onTickHaptic()
                }
            }
        } else if (ptr.key.spec.id == Mp02KeyId.BACKSPACE && !ptr.swipedWordDelete) {
            if (ptr.downX - x > metrics.keySizePx * 0.90f && abs(y - ptr.downY) < metrics.keySizePx) {
                ptr.swipedWordDelete = true
                lastCommittedChar = null
                cancelRunnable(pointerId)
                sink.onBackspaceWord()
                sink.onKeyHaptic()
            }
        } else if (!isModifierKey(ptr.key.spec.id) &&
            hypot(x - ptr.downX, y - ptr.downY) > metrics.keySizePx * 0.55f
        ) {
            cancelRunnable(pointerId)
        }
    }

    fun onPointerUp(pointerId: Int, nowMs: Long, config: Mp02InputConfig) {
        val ptr = activePointers.remove(pointerId)
        cancelRunnable(pointerId)
        if (ptr != null) {
            handlePointerRelease(ptr, nowMs, config)
        }
        sink.onRedrawRequested()
    }

    fun onCancel() {
        clearPointers()
        sink.onRedrawRequested()
    }

    fun activateOverlayCell(cell: OverlayCell, onRecordEmoji: (String) -> Unit, onCycleFinish: () -> Unit) {
        when {
            cell.commandAction == Mp02CommandAction.BACK_TO_KEYS -> {
                deckMode = Mp02DeckMode.KEYS
                sink.onOverlayChanged()
            }
            cell.commandAction == Mp02CommandAction.TOGGLE_FINISH -> {
                onCycleFinish()
                sink.onOverlayChanged()
                sink.onCommandAction(Mp02CommandAction.TOGGLE_FINISH)
            }
            cell.commandAction != null -> {
                sink.onCommandAction(cell.commandAction)
            }
            cell.emojiCategoryIndex != null -> {
                emojiCategoryIndex = cell.emojiCategoryIndex
                sink.onOverlayChanged()
            }
            cell.emojiValue != null -> {
                onRecordEmoji(cell.emojiValue)
                sink.onCommitText(cell.emojiValue)
                lastCommittedChar = null
            }
        }
        sink.onRedrawRequested()
    }

    fun clearPointers() {
        pointerRunnables.values.forEach { scheduler.cancel(it) }
        pointerRunnables.clear()
        activePointers.clear()
    }

    private fun handlePointerRelease(ptr: ActivePointer, nowMs: Long, config: Mp02InputConfig) {
        if (ptr.longPressTriggered || ptr.isTrackpadDragging || ptr.alreadyCommitted) return

        when (ptr.key.spec.id) {
            Mp02KeyId.SHIFT -> {
                if (!ptr.chordUsed) {
                    shiftState = when {
                        shiftState == ShiftState.CAPS_LOCK -> ShiftState.OFF
                        lastShiftTapMs > 0L && nowMs - lastShiftTapMs < 350L -> ShiftState.CAPS_LOCK
                        shiftState == ShiftState.ONE_SHOT -> ShiftState.OFF
                        else -> ShiftState.ONE_SHOT
                    }
                    lastShiftTapMs = nowMs
                }
            }
            Mp02KeyId.ALT -> {
                if (!ptr.chordUsed) {
                    if (lastAltTapMs > 0L && nowMs - lastAltTapMs < 350L) {
                        activeLayer = Mp02Layer.ALT_NUM
                        altState = LayerLockState.LOCKED
                    } else if (altState == LayerLockState.OFF) {
                        activeLayer = Mp02Layer.ALT_NUM
                        altState = LayerLockState.ONE_SHOT
                    } else {
                        activeLayer = Mp02Layer.ALPHA
                        altState = LayerLockState.OFF
                    }
                    lastAltTapMs = nowMs
                }
            }
            Mp02KeyId.CTRL -> {
                if (!ptr.chordUsed) {
                    if (activeLayer == Mp02Layer.ALT_NUM) {
                        emitKeyOutput(ptr.key.spec, forceAlt = false)
                    } else {
                        ctrlOneShot = !ctrlOneShot
                    }
                }
            }
            Mp02KeyId.SYM_LEFT, Mp02KeyId.SYM_RIGHT -> {
                if (activeLayer == Mp02Layer.ALPHA) {
                    activeLayer = Mp02Layer.ALT_NUM
                    altState = LayerLockState.LOCKED
                } else {
                    activeLayer = Mp02Layer.ALPHA
                    altState = LayerLockState.OFF
                }
            }
            Mp02KeyId.MINIMAL -> {
                deckMode = Mp02DeckMode.COMMAND
                sink.onOverlayChanged()
            }
            Mp02KeyId.SEARCH -> sink.onSearchKey()
            Mp02KeyId.ENTER -> {
                val shift = isKeyPressed(Mp02KeyId.SHIFT) || shiftState != ShiftState.OFF
                sink.onEnterKey(shift)
                if (shiftState == ShiftState.ONE_SHOT) shiftState = ShiftState.OFF
                lastCommittedChar = '\n'
            }
            Mp02KeyId.SPACE -> {
                if (config.doubleSpacePeriod && lastSpaceTapMs > 0L && nowMs - lastSpaceTapMs < 320L &&
                    lastCommittedChar != null && lastCommittedChar!!.isLetterOrDigit()
                ) {
                    sink.onBackspaceChar()
                    sink.onCommitText(". ")
                    lastCommittedChar = '.'
                    lastSpaceTapMs = 0L
                } else {
                    sink.onCommitText(" ")
                    lastSpaceTapMs = nowMs
                }
            }
            Mp02KeyId.BACKSPACE -> Unit
            else -> emitKeyOutput(ptr.key.spec, forceAlt = false)
        }
    }

    private fun emitKeyOutput(spec: Mp02KeySpec, forceAlt: Boolean) {
        val ctrlActive = isKeyPressed(Mp02KeyId.CTRL) || ctrlOneShot
        if (ctrlActive && !forceAlt) {
            ctrlOneShot = false
            lastCommittedChar = null
            sink.onCtrlChord(spec.id)
            return
        }

        val shiftActive = isKeyPressed(Mp02KeyId.SHIFT) || shiftState != ShiftState.OFF
        val altChord = isKeyPressed(Mp02KeyId.ALT)

        val output = when {
            forceAlt || altChord || activeLayer == Mp02Layer.ALT_NUM -> spec.altChar ?: spec.lowerChar
            shiftActive -> spec.upperChar ?: spec.lowerChar
            else -> spec.lowerChar
        }

        if (output != null) {
            sink.onCommitText(output)
            if (output.length == 1 && output[0] != ' ') {
                lastCommittedChar = output[0]
            }
        }

        if (!forceAlt && !isKeyPressed(Mp02KeyId.SHIFT) && shiftState == ShiftState.ONE_SHOT && activeLayer == Mp02Layer.ALPHA) {
            shiftState = ShiftState.OFF
        }
        if (!forceAlt && !altChord && altState == LayerLockState.ONE_SHOT) {
            altState = LayerLockState.OFF
            activeLayer = Mp02Layer.ALPHA
        }
    }

    private fun commitPendingRolloverKeys(nowMs: Long, config: Mp02InputConfig) {
        for (ptr in activePointers.values) {
            val id = ptr.key.spec.id
            if (!isModifierKey(id) && id != Mp02KeyId.BACKSPACE && id != Mp02KeyId.SPACE &&
                !ptr.alreadyCommitted && !ptr.longPressTriggered && !ptr.isTrackpadDragging
            ) {
                cancelRunnable(ptr.pointerId)
                handlePointerRelease(ptr, nowMs, config)
                ptr.alreadyCommitted = true
            }
        }
    }

    private fun markHeldModifiersChordUsed() {
        for (ptr in activePointers.values) {
            if (isModifierKey(ptr.key.spec.id)) {
                ptr.chordUsed = true
                cancelRunnable(ptr.pointerId)
            }
        }
    }

    private fun isModifierKey(id: Mp02KeyId): Boolean =
        id == Mp02KeyId.SHIFT || id == Mp02KeyId.ALT || id == Mp02KeyId.CTRL

    private fun isNavKey(id: Mp02KeyId): Boolean =
        id == Mp02KeyId.BACKSPACE || id == Mp02KeyId.ENTER || id == Mp02KeyId.SPACE ||
            id == Mp02KeyId.SYM_LEFT || id == Mp02KeyId.SYM_RIGHT ||
            id == Mp02KeyId.MINIMAL || id == Mp02KeyId.SEARCH

    private fun scheduleCharLongPress(pointerId: Int) {
        val r = Runnable {
            val ptr = activePointers[pointerId] ?: return@Runnable
            if (ptr.alreadyCommitted || ptr.isTrackpadDragging) return@Runnable
            ptr.longPressTriggered = true
            emitKeyOutput(ptr.key.spec, forceAlt = true)
            sink.onKeyHaptic()
            sink.onRedrawRequested()
        }
        pointerRunnables[pointerId] = r
        scheduler.postDelayed(r, 380L)
    }

    private fun scheduleShiftLongPress(pointerId: Int) {
        val r = Runnable {
            val ptr = activePointers[pointerId] ?: return@Runnable
            if (ptr.chordUsed) return@Runnable
            ptr.longPressTriggered = true
            shiftState = if (shiftState == ShiftState.CAPS_LOCK) ShiftState.OFF else ShiftState.CAPS_LOCK
            sink.onKeyHaptic()
            sink.onRedrawRequested()
        }
        pointerRunnables[pointerId] = r
        scheduler.postDelayed(r, 400L)
    }

    private fun scheduleAltLongPress(pointerId: Int) {
        val r = Runnable {
            val ptr = activePointers[pointerId] ?: return@Runnable
            if (ptr.chordUsed) return@Runnable
            ptr.longPressTriggered = true
            deckMode = Mp02DeckMode.EMOJI
            sink.onOverlayChanged()
            sink.onKeyHaptic()
            sink.onRedrawRequested()
        }
        pointerRunnables[pointerId] = r
        scheduler.postDelayed(r, 380L)
    }

    private fun scheduleSpaceLongPress(pointerId: Int) {
        val r = Runnable {
            val ptr = activePointers[pointerId] ?: return@Runnable
            if (ptr.isTrackpadDragging) return@Runnable
            ptr.longPressTriggered = true
            sink.onKeyHaptic()
            sink.onShowImePicker()
        }
        pointerRunnables[pointerId] = r
        scheduler.postDelayed(r, 460L)
    }

    private fun scheduleBackspaceRepeat(pointerId: Int) {
        val repeater = object : Runnable {
            override fun run() {
                val ptr = activePointers[pointerId] ?: return
                if (ptr.swipedWordDelete) return
                lastCommittedChar = null
                sink.onBackspaceChar()
                sink.onTickHaptic()
                scheduler.postDelayed(this, 48L)
            }
        }
        pointerRunnables[pointerId] = repeater
        scheduler.postDelayed(repeater, 340L)
    }

    private fun cancelRunnable(pointerId: Int) {
        pointerRunnables.remove(pointerId)?.let { scheduler.cancel(it) }
    }
}
