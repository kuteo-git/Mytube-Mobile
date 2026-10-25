package com.mytube.app.ui.tv

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How wide a card is on each shelf of the television's Home: YouTube's TV app's
 * own sizes, measured on the household's Chromecast. The shelf the remote is on
 * is the large one, two and a half across; every other shelf is three and a
 * half across.
 */
class ShelfCardWidthTest {

    @Test
    fun `before any shelf has been on, the first shelf is the large one`() {
        assertEquals(360.dp, shelfCardWidth(shelfIndex = 0, current = NO_SHELF))
        assertEquals(264.dp, shelfCardWidth(shelfIndex = 1, current = NO_SHELF))
    }

    @Test
    fun `the shelf the remote moved down to is the large one`() {
        // Reported from the television: every shelf under the first stayed
        // small with the remote on it. YouTube's grows the row it moves to.
        assertEquals(360.dp, shelfCardWidth(shelfIndex = 3, current = 3))
    }

    @Test
    fun `every shelf the remote is not on is 264dp, the first included`() {
        assertEquals(264.dp, shelfCardWidth(shelfIndex = 0, current = 3))
        assertEquals(264.dp, shelfCardWidth(shelfIndex = 2, current = 3))
        assertEquals(264.dp, shelfCardWidth(shelfIndex = 40, current = 3))
    }
}
