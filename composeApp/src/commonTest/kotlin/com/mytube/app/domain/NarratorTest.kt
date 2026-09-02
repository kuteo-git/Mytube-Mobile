package com.mytube.app.domain

import com.mytube.app.domain.model.NarrationClip
import com.mytube.app.domain.player.NarrationHost
import com.mytube.app.domain.player.Narrator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The narration loop, tested without a device.
 *
 * This is the argument for having moved it into `commonMain`: while it lived in
 * `ExoVideoPlayer` and `AvVideoPlayer` it could only be checked by listening to
 * a phone, so the two copies were free to drift and nothing would have said so.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NarratorTest {

    private class FakeHost : NarrationHost {
        override var videoIsPlaying = true
        override var videoPositionSeconds = 0.0
        var volume = 1f
        val spoken = mutableListOf<String>()
        var silenced = 0

        override fun videoVolume() = volume
        override fun setVideoVolume(level: Float) { volume = level }
        /** The level each line started at, so the sliders can be asserted. */
        val spokenAt = mutableListOf<Float>()
        /** Every address handed over ahead of time, in order. */
        val prepared = mutableListOf<String>()
        override fun prepare(url: String) { prepared.add(url) }
        override fun speak(url: String, volume: Float) {
            spoken.add(url)
            spokenAt.add(volume)
            voiceAt = volume
        }
        var voiceAt = 0f
        override fun setSpeechVolume(level: Float) { voiceAt = level }
        override fun silence() { silenced++ }
    }

    private fun clip(start: Double, end: Double, url: String) =
        NarrationClip(
            startSeconds = start,
            durationSeconds = end - start,
            clipUrl = url,
            text = url,
        )

    @Test
    fun `speaks a line once while it is due, and ducks the video under it`() = runTest {
        val host = FakeHost()
        val narrator = Narrator(host, TestScope(testScheduler))

        narrator.setClips(listOf(clip(1.0, 3.0, "a.wav")))

        host.videoPositionSeconds = 1.5
        advanceTimeBy(300)
        assertEquals(listOf("a.wav"), host.spoken)
        // Ducked: the video is quieter than the level narration started from.
        assertTrue(host.volume < 1f, "video should be ducked, was ${host.volume}")

        // Still inside the same clip on the next tick — started once, not again.
        host.videoPositionSeconds = 2.0
        advanceTimeBy(300)
        assertEquals(listOf("a.wav"), host.spoken)

        narrator.release()
    }

    /** Past the end of a line, the video goes back to exactly where it was. */
    @Test
    fun `restores the viewer's own level rather than full volume`() = runTest {
        val host = FakeHost()
        host.volume = 0.4f
        val narrator = Narrator(host, TestScope(testScheduler))

        narrator.setClips(listOf(clip(1.0, 2.0, "a.wav")))
        host.videoPositionSeconds = 1.5
        advanceTimeBy(300)
        assertTrue(host.volume < 0.4f)

        host.videoPositionSeconds = 5.0
        advanceTimeBy(300)
        assertEquals(0.4f, host.volume)

        narrator.release()
    }

    /**
     * A clip started just before a pause must not carry on talking over a still
     * frame — the one behaviour that is obvious when you hear it and invisible
     * in the code.
     */
    @Test
    fun `stops speaking when the video is paused`() = runTest {
        val host = FakeHost()
        val narrator = Narrator(host, TestScope(testScheduler))

        narrator.setClips(listOf(clip(0.0, 10.0, "a.wav")))
        host.videoPositionSeconds = 1.0
        advanceTimeBy(300)
        assertEquals(1, host.spoken.size)

        host.videoIsPlaying = false
        advanceTimeBy(300)
        assertTrue(host.silenced > 0)
        assertEquals(1f, host.volume)

        narrator.release()
    }

    /** An empty list is how narration is switched off; there is no second call. */
    @Test
    fun `an empty list stops and restores`() = runTest {
        val host = FakeHost()
        val narrator = Narrator(host, TestScope(testScheduler))

        narrator.setClips(listOf(clip(0.0, 10.0, "a.wav")))
        host.videoPositionSeconds = 1.0
        advanceTimeBy(300)
        assertTrue(host.volume < 1f)

        narrator.setClips(emptyList())
        assertEquals(1f, host.volume)

        // The ticker is gone: moving the playhead speaks nothing more.
        host.videoPositionSeconds = 2.0
        advanceTimeBy(1000)
        assertEquals(1, host.spoken.size)
    }

    /**
     * The server's pass grows the list while it runs, so a longer list arrives
     * repeatedly and must not restart whatever is already speaking.
     */
    @Test
    fun `a longer list does not interrupt the line in progress`() = runTest {
        val host = FakeHost()
        val narrator = Narrator(host, TestScope(testScheduler))

        narrator.setClips(listOf(clip(0.0, 10.0, "a.wav")))
        host.videoPositionSeconds = 1.0
        advanceTimeBy(300)

        narrator.setClips(listOf(clip(0.0, 10.0, "a.wav"), clip(20.0, 22.0, "b.wav")))
        advanceTimeBy(300)
        assertEquals(listOf("a.wav"), host.spoken)

        host.videoPositionSeconds = 20.5
        advanceTimeBy(300)
        assertEquals(listOf("a.wav", "b.wav"), host.spoken)

        narrator.release()
    }

    /**
     * The two levels take effect on the line already speaking.
     *
     * The obvious implementation — call `speak` again at the new level — would
     * restart the sentence, so dragging the slider would make the current line
     * begin again on every frame of the drag. This asserts the clip was started
     * once and that both levels still moved.
     */
    @Test
    fun `changing the levels does not restart the line being spoken`() = runTest {
        val host = FakeHost()
        val narrator = Narrator(host, TestScope(testScheduler))

        narrator.setClips(listOf(clip(1.0, 30.0, "a.wav")))
        host.videoPositionSeconds = 2.0
        advanceTimeBy(300)
        assertEquals(listOf("a.wav"), host.spoken)

        narrator.setLevels(voice = 1.5f, duck = 0.05f)

        assertEquals(listOf("a.wav"), host.spoken)
        assertEquals(1.5f, host.voiceAt)
        assertEquals(0.05f, host.volume)

        // The ticker never stops on its own, and `runTest` drains the scheduler
        // when the body returns — so a narrator left running spins virtual time
        // for ever and the whole test task hangs with no failure to read.
        // Measured: it did, for four minutes, before this line.
        narrator.release()
    }

    /** Nothing is speaking, so there is nothing to re-level and nothing to start. */
    @Test
    fun `setting the levels while silent speaks nothing`() = runTest {
        val host = FakeHost()
        val narrator = Narrator(host, TestScope(testScheduler))

        narrator.setLevels(voice = 1.5f, duck = 0.05f)

        assertEquals(emptyList(), host.spoken)
        assertEquals(1f, host.volume)
        narrator.release()
    }

    @Test
    fun `buffers the next line while this one is speaking`() = runTest {
        val host = FakeHost()
        val narrator = Narrator(host, TestScope(testScheduler))

        narrator.setClips(listOf(clip(1.0, 3.0, "a.wav"), clip(3.0, 5.0, "b.wav")))

        host.videoPositionSeconds = 1.5
        advanceTimeBy(400)

        // The server leaves a line's audio no room inside its slot, so the next
        // one has to be fetched before it is due or its predecessor loses the
        // moment the fetch takes.
        assertEquals(listOf("b.wav"), host.prepared)
        assertEquals(listOf("a.wav"), host.spoken)
        // The ticker is `while (true)`, and `runTest` drains the scheduler when
        // the body ends — so a test that leaves it running advances virtual
        // time for ever. Every test here ends this way for that reason.
        narrator.release()
    }

    @Test
    fun `hands the same address over once, however many ticks pass`() = runTest {
        val host = FakeHost()
        val narrator = Narrator(host, TestScope(testScheduler))

        narrator.setClips(listOf(clip(1.0, 3.0, "a.wav"), clip(3.0, 5.0, "b.wav")))

        host.videoPositionSeconds = 1.5
        advanceTimeBy(1_000)

        assertEquals(listOf("b.wav"), host.prepared)
        narrator.release()
    }
}
