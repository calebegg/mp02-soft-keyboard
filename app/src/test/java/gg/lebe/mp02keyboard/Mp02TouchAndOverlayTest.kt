package gg.lebe.mp02keyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Mp02TouchAndOverlayTest {

    private class FakeSink : Mp02InputSink {
        val committed = StringBuilder()
        var backspaces = 0
        var wordBackspaces = 0
        var enters = 0
        var cursorMoves = 0
        var imePickers = 0
        val ctrlChords = mutableListOf<Mp02KeyId>()

        override fun onCommitText(text: String) { committed.append(text) }
        override fun onBackspaceChar() {
            backspaces++
            if (committed.isNotEmpty()) committed.deleteCharAt(committed.length - 1)
        }
        override fun onBackspaceWord() { wordBackspaces++ }
        override fun onEnterKey(shiftHeld: Boolean) { enters++ }
        override fun onSearchKey() {}
        override fun onCursorMove(delta: Int, select: Boolean) { cursorMoves += delta }
        override fun onCtrlChord(keyId: Mp02KeyId) { ctrlChords += keyId }
        override fun onCommandAction(action: Mp02CommandAction) {}
        override fun onShowImePicker() { imePickers++ }
        override fun onKeyHaptic() {}
        override fun onTickHaptic() {}
        override fun onRedrawRequested() {}
        override fun onOverlayChanged() {}
    }

    private class FakeScheduler : Mp02TimerScheduler {
        val pending = linkedSetOf<Runnable>()
        override fun postDelayed(runnable: Runnable, delayMs: Long) { pending.add(runnable) }
        override fun cancel(runnable: Runnable) { pending.remove(runnable) }
    }

    private fun testMetrics(): Mp02DeckMetrics {
        val pxMm = 415.636f / 25.4f
        return Mp02Geometry.computeLayout(1080, pxMm, pxMm)
    }

    private fun centerOf(m: Mp02DeckMetrics, id: Mp02KeyId): Pair<Float, Float> {
        val k = m.keys.first { it.spec.id == id }
        return k.centerX to k.centerY
    }

    @Test
    fun `two-thumb fast rollover commits characters in press order`() {
        val m = testMetrics()
        val sink = FakeSink()
        val ctrl = Mp02TouchController(sink, FakeScheduler())
        val cfg = Mp02InputConfig()
        val (tx, ty) = centerOf(m, Mp02KeyId.T)
        val (hx, hy) = centerOf(m, Mp02KeyId.H)

        // Press T, then press H before lifting T, then lift T before lifting H
        ctrl.onPointerDown(m, 0, tx, ty, 100L, cfg)
        ctrl.onPointerDown(m, 1, hx, hy, 120L, cfg)
        ctrl.onPointerUp(0, 140L, cfg)
        ctrl.onPointerUp(1, 160L, cfg)

        assertEquals("th", sink.committed.toString())
    }

    @Test
    fun `alt chord types printed symbol and returns to alpha`() {
        val m = testMetrics()
        val sink = FakeSink()
        val ctrl = Mp02TouchController(sink, FakeScheduler())
        val cfg = Mp02InputConfig()
        val (ax, ay) = centerOf(m, Mp02KeyId.ALT)
        val (wx, wy) = centerOf(m, Mp02KeyId.W)

        ctrl.onPointerDown(m, 0, ax, ay, 100L, cfg)
        ctrl.onPointerDown(m, 1, wx, wy, 120L, cfg)
        ctrl.onPointerUp(1, 150L, cfg)
        ctrl.onPointerUp(0, 180L, cfg)

        assertEquals("1", sink.committed.toString())
        assertEquals(Mp02Layer.ALPHA, ctrl.activeLayer)
    }

    @Test
    fun `123 toggles directly between alpha and alt_num lock`() {
        val m = testMetrics()
        val sink = FakeSink()
        val ctrl = Mp02TouchController(sink, FakeScheduler())
        val cfg = Mp02InputConfig()
        val (sx, sy) = centerOf(m, Mp02KeyId.SYM_LEFT)
        val (cx, cy) = centerOf(m, Mp02KeyId.CTRL)

        // Tap 123 -> locks ALT_NUM
        ctrl.onPointerDown(m, 0, sx, sy, 100L, cfg)
        ctrl.onPointerUp(0, 140L, cfg)
        assertEquals(Mp02Layer.ALT_NUM, ctrl.activeLayer)

        // Tap ctrl/0 while locked -> types '0'
        ctrl.onPointerDown(m, 0, cx, cy, 200L, cfg)
        ctrl.onPointerUp(0, 230L, cfg)
        assertEquals("0", sink.committed.toString())

        // Tap 123 again -> returns straight to ALPHA (no 2nd symbol page)
        ctrl.onPointerDown(m, 0, sx, sy, 300L, cfg)
        ctrl.onPointerUp(0, 340L, cfg)
        assertEquals(Mp02Layer.ALPHA, ctrl.activeLayer)
    }

    @Test
    fun `double space inserts period but backspace clears prior letter state`() {
        val m = testMetrics()
        val sink = FakeSink()
        val ctrl = Mp02TouchController(sink, FakeScheduler())
        val cfg = Mp02InputConfig()
        val (ax, ay) = centerOf(m, Mp02KeyId.A)
        val (spx, spy) = centerOf(m, Mp02KeyId.SPACE)
        val (bx, by) = centerOf(m, Mp02KeyId.BACKSPACE)

        // a + space + backspace + space -> "a " (NOT "a. ")
        ctrl.onPointerDown(m, 0, ax, ay, 100L, cfg); ctrl.onPointerUp(0, 120L, cfg)
        ctrl.onPointerDown(m, 0, spx, spy, 140L, cfg); ctrl.onPointerUp(0, 160L, cfg)
        ctrl.onPointerDown(m, 0, bx, by, 200L, cfg); ctrl.onPointerUp(0, 220L, cfg)
        ctrl.onPointerDown(m, 0, spx, spy, 240L, cfg); ctrl.onPointerUp(0, 260L, cfg)
        assertEquals("a ", sink.committed.toString())

        // b + space + space -> "a b. "
        val (btx, bty) = centerOf(m, Mp02KeyId.B)
        ctrl.onPointerDown(m, 0, btx, bty, 700L, cfg); ctrl.onPointerUp(0, 720L, cfg)
        ctrl.onPointerDown(m, 0, spx, spy, 740L, cfg); ctrl.onPointerUp(0, 760L, cfg)
        ctrl.onPointerDown(m, 0, spx, spy, 820L, cfg); ctrl.onPointerUp(0, 840L, cfg)
        assertEquals("a b. ", sink.committed.toString())
    }

    @Test
    fun `spacebar horizontal drag scrubs cursor without inserting space`() {
        val m = testMetrics()
        val sink = FakeSink()
        val ctrl = Mp02TouchController(sink, FakeScheduler())
        val cfg = Mp02InputConfig()
        val (spx, spy) = centerOf(m, Mp02KeyId.SPACE)

        ctrl.onPointerDown(m, 0, spx, spy, 100L, cfg)
        ctrl.onPointerMove(m, 0, spx + m.keySizePx * 1.5f, spy, cfg)
        ctrl.onPointerUp(0, 200L, cfg)

        assertEquals("", sink.committed.toString())
        assertTrue(sink.cursorMoves > 0)
    }

    @Test
    fun `command and emoji overlay cells stay inside deck bounds`() {
        val m = testMetrics()
        val cmdCells = Mp02OverlayBuilder.buildCommandCells(m, "PEARL")
        assertEquals(15, cmdCells.size)
        for (c in cmdCells) {
            assertTrue(c.rect.left >= 0f && c.rect.right <= m.widthPx)
            assertTrue(c.rect.top >= m.topBezelPx - 1f && c.rect.bottom <= m.heightPx)
        }

        val emojiCells = Mp02OverlayBuilder.buildEmojiCells(m, 1, emptyList())
        assertEquals(30, emojiCells.size) // 6 tabs + 24 emojis
        for (c in emojiCells) {
            assertTrue(c.rect.left >= 0f && c.rect.right <= m.widthPx)
            assertTrue(c.rect.top >= m.topBezelPx - 1f && c.rect.bottom <= m.heightPx)
        }
    }
}
