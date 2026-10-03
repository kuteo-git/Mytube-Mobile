package com.mytube.app.ui.watch

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** When ten seconds of playback have gone by since the last report. */
class ProgressDueTest {

    @Test
    fun tenSecondsOnIsDue() {
        assertFalse(progressDue(position = 19.0, lastReported = 10.0))
        assertTrue(progressDue(position = 20.0, lastReported = 10.0))
    }

    /**
     * Found by review: the check was `position - lastReported`, so after a seek
     * back the difference was negative and nothing was reported until the
     * playhead passed the old position again — minutes of a film with Continue
     * watching pointing somewhere the viewer had left.
     */
    @Test
    fun aSeekBackIsReportedToo() {
        assertTrue(progressDue(position = 30.0, lastReported = 600.0))
    }
}
