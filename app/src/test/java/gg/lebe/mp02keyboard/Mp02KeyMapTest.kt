package gg.lebe.mp02keyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class Mp02KeyMapTest {

    @Test
    fun `layout matches exact 37-key MP02 row distribution`() {
        assertEquals(4, Mp02KeyMap.ROWS.size)
        assertEquals(10, Mp02KeyMap.ROWS[0].size)
        assertEquals(10, Mp02KeyMap.ROWS[1].size)
        assertEquals(10, Mp02KeyMap.ROWS[2].size)
        assertEquals(7, Mp02KeyMap.ROWS[3].size)
        assertEquals(37, Mp02KeyMap.ALL_KEYS.size)
    }

    @Test
    fun `secondary alt legends match physical MP02 hardware keycaps`() {
        val byId = Mp02KeyMap.ALL_KEYS.associateBy { it.id }
        val expectedAlt = mapOf(
            Mp02KeyId.Q to "#",
            Mp02KeyId.W to "1",
            Mp02KeyId.E to "2",
            Mp02KeyId.R to "3",
            Mp02KeyId.T to "(",
            Mp02KeyId.Y to ")",
            Mp02KeyId.U to "_",
            Mp02KeyId.I to "-",
            Mp02KeyId.O to "+",
            Mp02KeyId.P to "@",
            Mp02KeyId.A to "*",
            Mp02KeyId.S to "4",
            Mp02KeyId.D to "5",
            Mp02KeyId.F to "6",
            Mp02KeyId.G to "/",
            Mp02KeyId.H to ":",
            Mp02KeyId.J to ";",
            Mp02KeyId.K to "'",
            Mp02KeyId.L to "\"",
            Mp02KeyId.Z to "7",
            Mp02KeyId.X to "8",
            Mp02KeyId.C to "9",
            Mp02KeyId.V to "%",
            Mp02KeyId.B to "?",
            Mp02KeyId.N to "!",
            Mp02KeyId.M to ",",
            Mp02KeyId.PERIOD to "$",
            Mp02KeyId.CTRL to "0"
        )
        for ((id, sym) in expectedAlt) {
            assertEquals("Alt mismatch for $id", sym, byId[id]?.altChar)
            assertEquals("Secondary label mismatch for $id", sym, byId[id]?.secondaryLabel)
        }
        assertTrue(byId[Mp02KeyId.F]?.hasHomingPip == true)
        assertTrue(byId[Mp02KeyId.J]?.hasHomingPip == true)
        assertEquals(2, Mp02KeyMap.ALL_KEYS.count { it.hasHomingPip })
    }

    @Test
    fun `only alpha and alt_num layers exist`() {
        assertEquals(listOf(Mp02Layer.ALPHA, Mp02Layer.ALT_NUM), Mp02Layer.entries)
    }

    @Test
    fun `pixel 10 pro dimensions preserve 1-to-1 square keys and centered spacebar`() {
        val pxPerMmX = 415.636f / 25.4f
        val pxPerMmY = 416.421f / 25.4f
        val metrics = Mp02Geometry.computeLayout(
            viewWidthPx = 1080,
            pxPerMmX = pxPerMmX,
            pxPerMmY = pxPerMmY,
            heightScale = 1.0f,
            bottomChinMm = 2.2f
        )

        assertEquals(37, metrics.keys.size)
        for (k in metrics.keys) {
            assertTrue("Left clipped: ${k.spec.id}", k.rect.left > 0f)
            assertTrue("Right clipped: ${k.spec.id}", k.rect.right < 1080f)
        }

        val q = metrics.keys.first { it.spec.id == Mp02KeyId.Q }
        assertTrue(abs(q.widthPx - q.heightPx) < 0.5f)
        assertTrue(metrics.keyUnitMm in 5.05f..5.35f)

        val space = metrics.keys.first { it.spec.id == Mp02KeyId.SPACE }
        assertTrue(abs(space.centerX - 540f) < 0.5f)
        val ratio = space.widthPx / q.widthPx
        assertTrue("Spacebar ratio $ratio", ratio in 3.2f..3.35f)
    }

    @Test
    fun `voronoi hit testing captures inter-key gaps cleanly`() {
        val pxPerMm = 415.636f / 25.4f
        val metrics = Mp02Geometry.computeLayout(1080, pxPerMm, pxPerMm)
        val q = metrics.keys.first { it.spec.id == Mp02KeyId.Q }
        val w = metrics.keys.first { it.spec.id == Mp02KeyId.W }

        val nearQ = Mp02Geometry.findHitKey(metrics, q.rect.right + 4f, q.centerY)
        assertNotNull(nearQ)
        assertEquals(Mp02KeyId.Q, nearQ?.spec?.id)

        val nearW = Mp02Geometry.findHitKey(metrics, w.rect.left - 4f, w.centerY)
        assertNotNull(nearW)
        assertEquals(Mp02KeyId.W, nearW?.spec?.id)

        val outside = Mp02Geometry.findHitKey(metrics, 540f, -200f)
        assertNull(outside)
    }

    @Test
    fun `finishes resolve onyx pearl ember and auto`() {
        assertEquals(Mp02Themes.Onyx, Mp02Themes.resolve(Mp02Finish.ONYX, systemDark = false))
        assertEquals(Mp02Themes.Pearl, Mp02Themes.resolve(Mp02Finish.PEARL, systemDark = true))
        assertEquals(Mp02Themes.Ember, Mp02Themes.resolve(Mp02Finish.EMBER, systemDark = true))
        assertFalse(Mp02Themes.Ember.isLight)
        assertTrue(Mp02Themes.Ember.deckMidColor != Mp02Themes.Onyx.deckMidColor)
        assertEquals(Mp02Themes.Ember.keyFaceMid, 0xFF202023.toInt())
        assertEquals(Mp02Themes.Onyx, Mp02Themes.resolve(Mp02Finish.AUTO, systemDark = true))
        assertEquals(Mp02Themes.Pearl, Mp02Themes.resolve(Mp02Finish.AUTO, systemDark = false))
    }
}
