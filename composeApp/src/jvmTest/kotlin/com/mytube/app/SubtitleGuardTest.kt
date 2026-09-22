package com.mytube.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Android must not claim to draw captions it has switched off.
 *
 * # Why this is a guard and not a unit test
 *
 * The fault is a contradiction **between two files**, and no test of either one
 * can see it:
 *
 *  * `VideoSurface.android.kt` hides Media3's `SubtitleView`, because it and
 *    this app's `SubtitleOverlay` disagreed about line breaks and drew every
 *    cue twice;
 *  * `ExoVideoPlayer.rendersSubtitles` said `true`, and `WatchViewModel.loadCues`
 *    reads it as *"the player has this covered"* and does not fetch the file.
 *
 * Each was right on the day it was written. Together they removed both ways of
 * getting a caption onto the screen, and a ViewModel test would have been green
 * throughout — its fake player answers `false`, which is the case that works.
 *
 * So the rule is the pairing: **if the view is hidden, the flag is false.**
 */
class SubtitleGuardTest {

    @Test
    fun aPlayerWhoseSubtitleViewIsHiddenDoesNotClaimToRenderThem() {
        val surface = File(
            "src/androidMain/kotlin/com/mytube/app/ui/watch/VideoSurface.android.kt",
        ).readText()
        val player = File(
            "src/androidMain/kotlin/com/mytube/app/player/ExoVideoPlayer.kt",
        ).readText()

        val viewHidden = HIDDEN.containsMatchIn(surface)
        val claimsToRender = CLAIMS.containsMatchIn(player)

        assertFalse(
            viewHidden && claimsToRender,
            """
            `VideoSurface.android.kt` hides Media3's SubtitleView, and
            `ExoVideoPlayer` still reports `rendersSubtitles = true`.

            `WatchViewModel.loadCues` reads that flag as "the player has this
            covered" and does not fetch the .vtt — so with the view hidden as
            well, nothing draws a caption at all.

            Either show the view again, or say `false` here and let the app's
            own `SubtitleOverlay` do it.
            """.trimIndent(),
        )
    }

    private companion object {
        val HIDDEN = Regex("""subtitleView\?\.visibility\s*=\s*View\.GONE""")

        /** The declaration only — a `false` with a comment about `true` is fine. */
        val CLAIMS = Regex("""override val rendersSubtitles\s*:\s*Boolean\s*=\s*true""")
    }
}
