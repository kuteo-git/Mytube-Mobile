package com.mytube.app.ui.cast

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The phone's volume buttons and the speaker's volume, kept as one number.
 *
 * Two sources write it and each hears the other's write come back: the phone
 * reads back what was just set on it, and the speaker reports a value that is
 * a second old. Without these rules the two argue, and the volume a finger
 * just pressed is undone by the poll that left before it.
 */
class VolumeFollowerTest {

    @Test
    fun theSpeakersVolumeIsPutOnThePhoneWhenFirstHeard() {
        val f = VolumeFollower()
        assertEquals(40, f.speaker(40, now = 0))
    }

    @Test
    fun whatThePhoneReadsBackAfterBeingSetIsNotAPress() {
        val f = VolumeFollower()
        f.speaker(27, now = 0)
        // A phone in sixteen steps cannot hold 27; it reads back 25.
        assertNull(f.phone(25, now = 100))
        assertNull(f.phone(25, now = 200))
    }

    @Test
    fun aPressIsSentToTheSpeaker() {
        val f = VolumeFollower()
        f.speaker(50, now = 0)
        f.phone(50, now = 100)
        assertEquals(56, f.phone(56, now = 1_000))
    }

    @Test
    fun aReportOlderThanThePressDoesNotUndoIt() {
        val f = VolumeFollower()
        f.speaker(50, now = 0)
        f.phone(50, now = 100)
        f.phone(56, now = 1_000)
        assertNull(f.speaker(50, now = 1_200), "the poll left before the press")
        assertNull(f.speaker(56, now = 1_900), "the speaker has caught up; the phone is already there")
    }

    @Test
    fun theSameReportTwiceIsAppliedOnce() {
        val f = VolumeFollower()
        f.speaker(27, now = 0)
        f.phone(25, now = 100)
        assertNull(f.speaker(27, now = 1_000), "already applied; 25 is as near as the phone goes")
    }

    @Test
    fun aChangeMadeElsewhereComesToThePhone() {
        val f = VolumeFollower()
        f.speaker(50, now = 0)
        f.phone(50, now = 100)
        assertEquals(20, f.speaker(20, now = 5_000), "another phone, or the speaker's own buttons")
    }

    @Test
    fun aPressTheSpeakerNeverTookIsLetGoOf() {
        val f = VolumeFollower()
        f.speaker(50, now = 0)
        f.phone(50, now = 100)
        f.phone(56, now = 1_000)
        assertEquals(50, f.speaker(50, now = 4_000), "past the hold, the speaker's word is the truth")
    }

    @Test
    fun pressesInARowAreEachSent() {
        val f = VolumeFollower()
        f.speaker(50, now = 0)
        f.phone(50, now = 100)
        assertEquals(56, f.phone(56, now = 1_000))
        assertEquals(63, f.phone(63, now = 1_200))
        assertNull(f.speaker(56, now = 1_300), "the first press answering, behind the second")
    }

    @Test
    fun aReadingFromBeforeTheSetTookIsNotAPress() {
        val f = VolumeFollower()
        f.speaker(75, now = 0)
        // iOS changes its volume a moment after being asked.
        assertNull(f.phone(30, now = 50))
        assertNull(f.phone(75, now = 200), "the set arriving, not a finger")
        assertEquals(81, f.phone(81, now = 2_000))
    }
}
