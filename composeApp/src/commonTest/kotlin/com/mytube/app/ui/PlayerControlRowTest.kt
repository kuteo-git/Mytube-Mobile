package com.mytube.app.ui

import androidx.compose.ui.unit.dp
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.watch.controlRowBottom
import com.mytube.app.ui.watch.seekLineFromBottom
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * One margin under the clock and the zoom button, in both modes.
 *
 * The case this file exists for is the last one. Fullscreen cleared the seek
 * bar's whole 32dp **touch target** and then added the margin on top, while the
 * line it needed to clear is drawn along that target's bottom edge — so the gap
 * was 40dp in fullscreen against 8dp windowed, measured through Compose's own
 * coordinates on a landscape emulator at 933px and 1038px.
 */
class PlayerControlRowTest {

    @Test
    fun `outside fullscreen the row keeps one margin above the picture's edge`() {
        assertEquals(Space.sm, controlRowBottom(fullscreen = false, navigationInset = 0.dp))
    }

    @Test
    fun `the navigation inset does not reach a row inside the picture`() {
        // The bar there is the picture's own bottom edge, which is nowhere near
        // the home indicator.
        assertEquals(
            controlRowBottom(fullscreen = false, navigationInset = 0.dp),
            controlRowBottom(fullscreen = false, navigationInset = 34.dp),
        )
    }

    @Test
    fun `the real inset is used in fullscreen, not a constant`() {
        // 34 is an iPhone with a home indicator and 0 is a phone with buttons.
        assertEquals(Space.lg, seekLineFromBottom(0.dp))
        assertEquals(34.dp + Space.lg, seekLineFromBottom(34.dp))
    }

    @Test
    fun `the gap under the row is the same margin in both modes`() {
        // **The reported bug, as an invariant.** Before the fix this side was
        // `Space.sm + 32.dp` and the two were five times apart.
        for (inset in listOf(0.dp, 24.dp, 34.dp)) {
            val aboveTheLine = controlRowBottom(fullscreen = true, inset) - seekLineFromBottom(inset)
            assertEquals(
                controlRowBottom(fullscreen = false, inset),
                aboveTheLine,
                "inset=$inset",
            )
        }
    }
}
