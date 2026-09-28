package com.mytube.app.domain

import com.mytube.app.domain.model.Storyboard
import com.mytube.app.domain.model.frameAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which still is drawn for a moment.
 *
 * A pure function with a test for the reason `wholeSeconds`, `levelsFor` and
 * `barTravel` are ones: nothing in the type system catches an off-by-one here,
 * and getting it wrong does not fail — it draws a confident picture of the wrong
 * scene for the whole video.
 *
 * The geometry is what the running gateway actually answered, not an invented
 * shape. Same rule `StreamMappingTest` was written under.
 */
class StoryboardTest {

    /** Big Buck Bunny: 635s, six sheets of a 5x5 grid of 160x90 stills. */
    private val bunny = Storyboard(
        tileWidth = 160,
        tileHeight = 90,
        rows = 5,
        columns = 5,
        intervalSeconds = 124.02 / 25,
        sprites = listOf("v/storyboard/0.webp", "1", "2", "3", "4", "v/storyboard/5.webp"),
    )

    /** gEWF0LL4IPA, a portrait upload: the same rung, 50 wide instead of 160. */
    private val portrait = Storyboard(
        tileWidth = 50,
        tileHeight = 90,
        rows = 5,
        columns = 5,
        intervalSeconds = 48.958333 / 25,
        sprites = listOf("a.webp", "b.webp", "c.webp"),
    )

    @Test
    fun opensAtTheTopLeftOfTheFirstSheet() {
        val frame = bunny.frameAt(0.0)
        assertEquals("v/storyboard/0.webp", frame?.sprite)
        assertEquals(0, frame?.x)
        assertEquals(0, frame?.y)
    }

    @Test
    fun walksARowBeforeStartingTheNext() {
        // Row-major. Read the other way and every moment draws a still five
        // places away, for the whole video, with nothing failing — which is the
        // reason this test is here rather than the arithmetic being obvious.
        assertEquals(160, bunny.frameAt(bunny.intervalSeconds)?.x)
        assertEquals(0, bunny.frameAt(bunny.intervalSeconds)?.y)
        assertEquals(640, bunny.frameAt(bunny.intervalSeconds * 4)?.x)
        assertEquals(0, bunny.frameAt(bunny.intervalSeconds * 4)?.y)
        assertEquals(0, bunny.frameAt(bunny.intervalSeconds * 5)?.x)
        assertEquals(90, bunny.frameAt(bunny.intervalSeconds * 5)?.y)
    }

    @Test
    fun crossesIntoTheNextSheetWhenTheGridIsFull() {
        val last = bunny.frameAt(bunny.intervalSeconds * 24)
        assertEquals("v/storyboard/0.webp", last?.sprite)
        assertEquals(640, last?.x)
        assertEquals(360, last?.y)

        val next = bunny.frameAt(bunny.intervalSeconds * 25)
        assertEquals("1", next?.sprite)
        assertEquals(0, next?.x)
        assertEquals(0, next?.y)
    }

    @Test
    fun stillDrawsAStillAtTheVeryEndOfTheVideo() {
        // 635s is past the 128 stills this video has and lands in the partly
        // filled last sheet. Without the clamp the end of every bar has no
        // picture — which is exactly where somebody hunting the closing scene
        // drags to.
        assertEquals("v/storyboard/5.webp", bunny.frameAt(635.0)?.sprite)
    }

    @Test
    fun neverReachesPastTheLastSheet() {
        val frame = bunny.frameAt(99_999.0)
        assertEquals("v/storyboard/5.webp", frame?.sprite)
        assertEquals(640, frame?.x)
        assertEquals(360, frame?.y)
    }

    @Test
    fun treatsAMomentBeforeTheBeginningAsTheBeginning() {
        // A player reports a position before it has loaded, and on some that is
        // negative — which is not a place in a video. The same guard
        // `wholeSeconds` carries, for the same reason.
        val frame = bunny.frameAt(-5.0)
        assertEquals("v/storyboard/0.webp", frame?.sprite)
        assertEquals(0, frame?.x)
        assertEquals(0, frame?.y)
    }

    @Test
    fun aPortraitLadderScalesItsWidthAndNotItsHeight() {
        // The rungs are named by height, so a portrait video arrives at the same
        // 90 with a narrower tile. A preview sized from the width would come out
        // three times too tall here.
        val frame = portrait.frameAt(portrait.intervalSeconds * 6)
        assertEquals(50, frame?.x)
        assertEquals(90, frame?.y)
    }

    @Test
    fun drawsNothingRatherThanTheOpeningShotWhenThereIsNoBoard() {
        // Every one of these would otherwise point at sheet zero, which looks
        // like a preview that works and is wrong everywhere but the start.
        assertNull(bunny.copy(sprites = emptyList()).frameAt(10.0))
        assertNull(bunny.copy(intervalSeconds = 0.0).frameAt(10.0))
        assertNull(bunny.copy(rows = 0).frameAt(10.0))
        assertNull(bunny.copy(columns = 0).frameAt(10.0))
        assertNull(bunny.copy(tileWidth = 0).frameAt(10.0))
        assertNull(bunny.copy(tileHeight = 0).frameAt(10.0))
        assertNull(bunny.frameAt(Double.NaN))
    }

    @Test
    fun aBoardThatCannotBeDrawnFromSaysSo() {
        assertTrue(bunny.isDrawable)
        assertTrue(!bunny.copy(sprites = emptyList()).isDrawable)
        assertTrue(!bunny.copy(intervalSeconds = 0.0).isDrawable)
        assertEquals(25, bunny.framesPerSheet)
    }
}
