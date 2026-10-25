package com.mytube.app.ui.tv

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How wide a card is on each shelf of the television's Home: YouTube's TV app's
 * own sizes, two and a half across on the first shelf and three and a half on
 * every other.
 */
class ShelfCardWidthTest {

    @Test
    fun `the first shelf's cards are 360dp`() {
        assertEquals(360.dp, shelfCardWidth(0))
    }

    @Test
    fun `every other shelf's cards are 264dp`() {
        assertEquals(264.dp, shelfCardWidth(1))
        assertEquals(264.dp, shelfCardWidth(2))
        assertEquals(264.dp, shelfCardWidth(40))
    }
}
