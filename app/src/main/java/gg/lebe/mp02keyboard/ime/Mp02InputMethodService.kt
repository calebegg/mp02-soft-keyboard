package gg.lebe.mp02keyboard.ime

import android.content.SharedPreferences
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.view.WindowCompat

class Mp02InputMethodService : InputMethodService(), Mp02KeyboardView.Listener {

    private var keyboardView: Mp02KeyboardView? = null
    private var prefs: Mp02Preferences? = null

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        keyboardView?.let { kv ->
            kv.refreshConfigAndRedraw()
            syncNavigationBarColor()
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Mp02Preferences(this).also { it.registerListener(prefListener) }
    }

    override fun onDestroy() {
        prefs?.unregisterListener(prefListener)
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val kv = Mp02KeyboardView(this).apply {
            listener = this@Mp02InputMethodService
        }
        keyboardView = kv
        return kv
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val kv = keyboardView ?: return
        kv.refreshConfigAndRedraw()
        kv.resetModifiersForNewField()
        syncNavigationBarColor()

        val inputClass = (info?.inputType ?: 0) and InputType.TYPE_MASK_CLASS
        val isNumeric = inputClass == InputType.TYPE_CLASS_NUMBER ||
            inputClass == InputType.TYPE_CLASS_PHONE
        kv.setInputNumericHint(isNumeric)

        kv.hasSpecialImeAction = hasCustomImeAction(info)
        if (!isNumeric) {
            updateAutoShiftState(info)
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        keyboardView?.clearPointers()
        super.onFinishInputView(finishingInput)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        keyboardView?.refreshConfigAndRedraw()
        syncNavigationBarColor()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        updateAutoShiftState(currentInputEditorInfo)
    }

    override fun onCommitText(text: String) {
        val ic = currentInputConnection ?: return
        ic.commitText(text, 1)
        updateAutoShiftState(currentInputEditorInfo)
    }

    override fun onBackspaceChar() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        if (info == null || info.inputType == InputType.TYPE_NULL) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            return
        }
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            ic.deleteSurroundingTextInCodePoints(1, 0)
        }
        updateAutoShiftState(info)
    }

    override fun onBackspaceWord() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        if (info == null || info.inputType == InputType.TYPE_NULL) {
            sendCtrlKeyEvent(KeyEvent.KEYCODE_W)
            return
        }
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
            updateAutoShiftState(info)
            return
        }
        val before = ic.getTextBeforeCursor(80, 0) ?: ""
        if (before.isEmpty()) return
        var idx = before.length
        while (idx > 0 && before[idx - 1].isWhitespace()) idx--
        while (idx > 0 && !before[idx - 1].isWhitespace()) idx--
        val deleteCount = before.length - idx
        if (deleteCount > 0) {
            ic.deleteSurroundingText(deleteCount, 0)
        }
        updateAutoShiftState(info)
    }

    override fun onEnterKey(shiftHeld: Boolean) {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        if (!shiftHeld && hasCustomImeAction(info)) {
            val action = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
            if (ic.performEditorAction(action)) return
        }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
    }

    override fun onSearchKey() {
        val ic = currentInputConnection
        val info = currentInputEditorInfo
        if (hasCustomImeAction(info) && ic != null) {
            val action = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
            if (ic.performEditorAction(action)) return
        }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_SEARCH)
    }

    override fun onCursorMove(delta: Int, select: Boolean) {
        if (delta == 0) return
        val keyCode = if (delta < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        val count = kotlin.math.abs(delta).coerceAtMost(8)
        val meta = if (select) KeyEvent.META_SHIFT_ON else 0
        repeat(count) {
            if (meta != 0) {
                sendKeyWithMeta(keyCode, meta)
            } else {
                sendDownUpKeyEvents(keyCode)
            }
        }
    }

    override fun onCtrlChord(keyId: Mp02KeyId) {
        val ic = currentInputConnection ?: return
        val isRawTerminal = (currentInputEditorInfo?.inputType ?: 0) == InputType.TYPE_NULL
        if (!isRawTerminal) {
            when (keyId) {
                Mp02KeyId.A -> { ic.performContextMenuAction(android.R.id.selectAll); return }
                Mp02KeyId.C -> { ic.performContextMenuAction(android.R.id.copy); return }
                Mp02KeyId.X -> { ic.performContextMenuAction(android.R.id.cut); return }
                Mp02KeyId.V -> { ic.performContextMenuAction(android.R.id.paste); return }
                Mp02KeyId.Z -> {
                    if (ic.performContextMenuAction(android.R.id.undo)) return
                }
                Mp02KeyId.Y -> {
                    if (ic.performContextMenuAction(android.R.id.redo)) return
                }
                Mp02KeyId.H -> { sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT); return }
                Mp02KeyId.J -> { sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_DOWN); return }
                Mp02KeyId.K -> { sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_UP); return }
                Mp02KeyId.L -> { sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT); return }
                else -> Unit
            }
        }
        val keyCode = keyIdToAndroidKeyCode(keyId) ?: return
        sendCtrlKeyEvent(keyCode)
    }

    override fun onShowImePicker() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        imm.showInputMethodPicker()
    }

    private fun updateAutoShiftState(info: EditorInfo?) {
        val kv = keyboardView ?: return
        if (!kv.prefs.autoCapEnabled || info == null) {
            kv.setAutoShift(false)
            return
        }
        if (kv.activeLayer != Mp02Layer.ALPHA) return
        val ic = currentInputConnection ?: return
        val capsMode = ic.getCursorCapsMode(info.inputType)
        kv.setAutoShift(capsMode != 0)
    }

    private fun hasCustomImeAction(info: EditorInfo?): Boolean {
        if (info == null) return false
        if ((info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return false
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        return action == EditorInfo.IME_ACTION_GO ||
            action == EditorInfo.IME_ACTION_SEARCH ||
            action == EditorInfo.IME_ACTION_SEND ||
            action == EditorInfo.IME_ACTION_NEXT ||
            action == EditorInfo.IME_ACTION_DONE
    }

    private fun syncNavigationBarColor() {
        val kv = keyboardView ?: return
        val w = window?.window ?: return
        w.navigationBarColor = kv.getCurrentDeckBottomColor()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            w.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(w, w.decorView).isAppearanceLightNavigationBars =
            kv.isCurrentThemeLight()
    }

    private fun sendCtrlKeyEvent(keyCode: Int) {
        sendKeyWithMeta(keyCode, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON)
    }

    private fun sendKeyWithMeta(keyCode: Int, metaState: Int) {
        val ic = currentInputConnection ?: return
        val now = android.os.SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, metaState))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, metaState))
    }

    private fun keyIdToAndroidKeyCode(id: Mp02KeyId): Int? = when (id) {
        Mp02KeyId.A -> KeyEvent.KEYCODE_A
        Mp02KeyId.B -> KeyEvent.KEYCODE_B
        Mp02KeyId.C -> KeyEvent.KEYCODE_C
        Mp02KeyId.D -> KeyEvent.KEYCODE_D
        Mp02KeyId.E -> KeyEvent.KEYCODE_E
        Mp02KeyId.F -> KeyEvent.KEYCODE_F
        Mp02KeyId.G -> KeyEvent.KEYCODE_G
        Mp02KeyId.H -> KeyEvent.KEYCODE_H
        Mp02KeyId.I -> KeyEvent.KEYCODE_I
        Mp02KeyId.J -> KeyEvent.KEYCODE_J
        Mp02KeyId.K -> KeyEvent.KEYCODE_K
        Mp02KeyId.L -> KeyEvent.KEYCODE_L
        Mp02KeyId.M -> KeyEvent.KEYCODE_M
        Mp02KeyId.N -> KeyEvent.KEYCODE_N
        Mp02KeyId.O -> KeyEvent.KEYCODE_O
        Mp02KeyId.P -> KeyEvent.KEYCODE_P
        Mp02KeyId.Q -> KeyEvent.KEYCODE_Q
        Mp02KeyId.R -> KeyEvent.KEYCODE_R
        Mp02KeyId.S -> KeyEvent.KEYCODE_S
        Mp02KeyId.T -> KeyEvent.KEYCODE_T
        Mp02KeyId.U -> KeyEvent.KEYCODE_U
        Mp02KeyId.V -> KeyEvent.KEYCODE_V
        Mp02KeyId.W -> KeyEvent.KEYCODE_W
        Mp02KeyId.X -> KeyEvent.KEYCODE_X
        Mp02KeyId.Y -> KeyEvent.KEYCODE_Y
        Mp02KeyId.Z -> KeyEvent.KEYCODE_Z
        Mp02KeyId.PERIOD -> KeyEvent.KEYCODE_PERIOD
        Mp02KeyId.SPACE -> KeyEvent.KEYCODE_SPACE
        else -> null
    }
}
