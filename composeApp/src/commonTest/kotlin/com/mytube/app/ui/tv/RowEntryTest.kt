package com.mytube.app.ui.tv

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which card a row of cards gives focus to as the remote comes into it from
 * above or below.
 *
 * Reported from the sofa: moving down from the first shelf landed on the
 * second shelf's *second* card, every time. The search Compose does by
 * default is geometric, and a shelf that has scrolled puts its focused card at
 * a third of the screen — over the next shelf's second card. YouTube's TV app
 * goes to the card a shelf was left on, or its first.
 */
class RowEntryTest {

    private val ids = listOf("a", "b", "c", "d")

    @Test
    fun `a row never visited is entered at its first card`() {
        assertEquals(0, rowEntryIndex(ids, lastId = "", lastIndex = 0))
    }

    @Test
    fun `a row is entered at the card it was left on`() {
        assertEquals(2, rowEntryIndex(ids, lastId = "c", lastIndex = 2))
    }

    /**
     * Coming back from the player, Continue watching puts the video just
     * watched first. The card left on is followed to where it went, rather
     * than the place it used to be, which now holds something else.
     */
    @Test
    fun `the card left on is followed when the row reorders`() {
        assertEquals(0, rowEntryIndex(listOf("c", "a", "b", "d"), lastId = "c", lastIndex = 2))
    }

    /** Missed drops a video once it has been watched: the place it was is the nearest thing. */
    @Test
    fun `a card that has gone leaves its place, kept inside the row`() {
        assertEquals(1, rowEntryIndex(listOf("a", "b", "d"), lastId = "c", lastIndex = 1))
        assertEquals(1, rowEntryIndex(listOf("a", "b"), lastId = "z", lastIndex = 5))
    }

    /** A place remembered before the row could have one is still a place in the row. */
    @Test
    fun `a negative place is the row's first card`() {
        assertEquals(0, rowEntryIndex(ids, lastId = "z", lastIndex = -3))
    }

    /** The id wins over the place, even when the place is still inside the row. */
    @Test
    fun `the card left on is followed even past the place it was`() {
        assertEquals(3, rowEntryIndex(listOf("a", "b", "c", "d"), lastId = "d", lastIndex = 0))
    }

    @Test
    fun `an empty row is entered at nothing in particular`() {
        assertEquals(0, rowEntryIndex(emptyList(), lastId = "c", lastIndex = 3))
    }
}

/**
 * A card focus is on stays in its row until focus leaves it.
 *
 * Measured on the emulator: a video opened from Missed, watched, put away —
 * Home caught up, Missed no longer had it, and with the focused card gone
 * focus fell out of the page onto the rail, which opened over the shelves.
 */
class KeptInPlaceTest {

    private fun ids(vararg id: String) = id.toList()

    @Test
    fun `a focused card the new list has dropped is kept where it was`() {
        assertEquals(
            ids("a", "c", "d"),
            keptInPlace(ids("a", "d"), focused = "c", at = 1, id = { it }),
        )
    }

    @Test
    fun `a focused card the new list still has is left where the new list puts it`() {
        assertEquals(
            ids("c", "a", "d"),
            keptInPlace(ids("c", "a", "d"), focused = "c", at = 1, id = { it }),
        )
    }

    @Test
    fun `nothing focused keeps nothing`() {
        assertEquals(ids("a", "d"), keptInPlace(ids("a", "d"), focused = null, at = 1, id = { it }))
    }

    @Test
    fun `a focused card kept at the start goes first`() {
        assertEquals(ids("c", "a", "d"), keptInPlace(ids("a", "d"), focused = "c", at = 0, id = { it }))
    }

    @Test
    fun `a negative place is the start`() {
        assertEquals(ids("c", "a"), keptInPlace(ids("a"), focused = "c", at = -2, id = { it }))
    }

    /** Missed answering with nothing at all still leaves the card focus is on. */
    @Test
    fun `a focused card the new list emptied out of is all that is left`() {
        assertEquals(ids("c"), keptInPlace(emptyList(), focused = "c", at = 3, id = { it }))
    }

    /** Matched by id, not by equality: the fresh answer's copy of a video is a different object. */
    @Test
    fun `a focused card is matched by its id`() {
        val fresh = listOf("a" to "new title", "c" to "new title")
        assertEquals(fresh, keptInPlace(fresh, focused = "c" to "old title", at = 0, id = { it.first }))
    }

    @Test
    fun `a place past the new end is the end`() {
        assertEquals(ids("a", "c"), keptInPlace(ids("a"), focused = "c", at = 4, id = { it }))
    }
}

/**
 * A row's memory of where it was left, held above the lazy column.
 *
 * What it records is what [rowEntryIndex] reads; and picking the rail's tab
 * again must send every row back to its first card, or Home's "back to the
 * top" would come back to the top with every shelf still scrolled.
 */
class RowMemoryTest {

    @Test
    fun `a fresh row remembers nothing`() {
        val memory = RowMemory()
        assertEquals("", memory.lastId)
        assertEquals(0, memory.lastIndex)
    }

    @Test
    fun `focusing a card remembers both its id and its place`() {
        val memory = RowMemory()
        memory.focused(index = 2, id = "c")

        assertEquals("c", memory.lastId)
        assertEquals(2, memory.lastIndex)
        assertEquals(2, rowEntryIndex(listOf("a", "b", "c"), memory.lastId, memory.lastIndex))
    }

    @Test
    fun `forgetting sends the row back to its first card`() {
        val memory = RowMemory()
        memory.focused(index = 2, id = "c")

        memory.forget()

        assertEquals("", memory.lastId)
        assertEquals(0, memory.lastIndex)
        assertEquals(0, rowEntryIndex(listOf("c", "a", "b"), memory.lastId, memory.lastIndex))
    }
}
