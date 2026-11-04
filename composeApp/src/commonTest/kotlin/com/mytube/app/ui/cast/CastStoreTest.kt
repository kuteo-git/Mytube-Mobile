package com.mytube.app.ui.cast

import com.mytube.app.domain.model.CastAnswer
import com.mytube.app.domain.model.CastDevice
import com.mytube.app.domain.model.CastDeviceKind
import com.mytube.app.domain.model.CastEnd
import com.mytube.app.domain.model.CastHandback
import com.mytube.app.domain.model.CastItem
import com.mytube.app.domain.model.CastNarration
import com.mytube.app.domain.model.CastRefusal
import com.mytube.app.domain.model.CastSession
import com.mytube.app.domain.model.CastState
import com.mytube.app.domain.repository.CastRepository
import com.mytube.app.domain.repository.PreferencesRepository
import com.mytube.app.ui.watch.InstantPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val r1 = CastDevice("r1-living", "Loa R1", CastDeviceKind.R1)

private fun session(
    state: CastState = CastState.Playing,
    end: CastEnd = CastEnd.Running,
    index: Int = 0,
    narration: CastNarration = CastNarration.Off,
) = CastSession(
    id = "s1", device = r1, state = state, end = end,
    queue = listOf("a", "b", "c").map { CastItem(it, "Song ${it.uppercase()}", "Chan", "", 200) },
    currentIndex = index, positionSeconds = 30, durationSeconds = 200, volume = 0, version = 4,
    narration = narration,
)

private class FakeCast : CastRepository {
    var sessionCalls = 0
    var sessionsCalls = 0
    var remote: CastAnswer<CastSession> = CastAnswer.Done(session())
    var queueAnswer: CastAnswer<CastSession> = CastAnswer.Done(session())
    val sent = mutableListOf<String>()

    override suspend fun devices() = listOf(r1)
    var running: List<CastSession> = emptyList()
    override suspend fun sessions(): CastAnswer<List<CastSession>> {
        sessionsCalls++
        return CastAnswer.Done(running)
    }
    override suspend fun session(id: String): CastAnswer<CastSession> {
        sessionCalls++
        return remote
    }
    override suspend fun start(
        deviceId: String,
        videoIds: List<String>,
        startPositionSeconds: Int,
        narration: Boolean,
    ): CastAnswer<CastSession> {
        sent += "start $deviceId $videoIds $startPositionSeconds" + if (narration) " narrated" else ""
        return CastAnswer.Done(session(CastState.Loading))
    }
    private fun cmd(name: String): CastAnswer<CastSession> {
        sent += name
        return CastAnswer.Done(session())
    }
    override suspend fun pause(id: String) = cmd("pause")
    override suspend fun resume(id: String) = cmd("resume")
    override suspend fun next(id: String) = cmd("next")
    override suspend fun stop(id: String) = cmd("stop")
    override suspend fun seek(id: String, positionSeconds: Int) = cmd("seek $positionSeconds")
    override suspend fun volume(id: String, percent: Int) = cmd("volume $percent")
    override suspend fun jump(id: String, index: Int) = cmd("jump $index")
    override suspend fun narration(id: String, on: Boolean) = cmd("narration $on")
    override suspend fun replaceQueue(id: String, version: Int, videoIds: List<String>, currentIndex: Int): CastAnswer<CastSession> {
        sent += "queue $version $videoIds $currentIndex"
        return queueAnswer
    }
    override suspend fun handoff(id: String): CastAnswer<CastHandback> {
        sent += "handoff"
        running = emptyList()
        return CastAnswer.Done(CastHandback("a", 130))
    }
}

/** The phone's narration preference; the rest answers as a fresh install. */
class Prefs(var narration: Boolean = false) : PreferencesRepository by InstantPreferences {
    override suspend fun narration() = narration
    override suspend fun setNarration(on: Boolean) {
        narration = on
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CastStoreTest {

    private fun TestScope.store(fake: FakeCast, upNext: List<String> = emptyList(), prefs: Prefs = Prefs()) =
        CastStore(fake, prefs, backgroundScope, upNext = { upNext })

    @Test
    fun theRemoteIsAskedEverySecondAndTheListEveryFive() {
        assertEquals(1_000L, castPollMillis(remoteOpen = true, foreground = true))
        assertEquals(5_000L, castPollMillis(remoteOpen = false, foreground = true))
        assertNull(castPollMillis(remoteOpen = true, foreground = false))
        assertNull(castPollMillis(remoteOpen = false, foreground = false))
    }

    @Test
    fun anOpenRemoteIsPolledEverySecondAndNothingInTheBackground() = runTest {
        val fake = FakeCast()
        val store = store(fake)
        store.openRemote("s1")
        runCurrent()
        val opened = fake.sessionCalls
        advanceTimeBy(3_100)
        assertEquals(opened + 3, fake.sessionCalls)

        store.setForeground(false)
        val hidden = fake.sessionCalls + fake.sessionsCalls
        advanceTimeBy(10_000)
        assertEquals(hidden, fake.sessionCalls + fake.sessionsCalls, "asked while in the background")
    }

    @Test
    fun theRunningListIsPolledEveryFiveSecondsWithTheRemoteClosed() = runTest {
        val fake = FakeCast()
        store(fake).setForeground(true)
        runCurrent()
        val first = fake.sessionsCalls
        advanceTimeBy(5_100)
        assertEquals(first + 1, fake.sessionsCalls)
        assertEquals(0, fake.sessionCalls)
    }

    @Test
    fun startingSendsWholeSecondsAndOpensTheRemote() = runTest {
        val fake = FakeCast()
        val store = store(fake)
        store.start(r1, listOf("a", "b"), 89.6, narrateFirst = false)
        runCurrent()
        assertEquals("start r1-living [a, b] 90", fake.sent.single())
        assertIs<CastRemote.Showing>(store.state.value.remote)
    }

    @Test
    fun castingWhileNarratingAsksTheSpeakerToNarrate() = runTest {
        val fake = FakeCast()
        store(fake, prefs = Prefs(narration = true)).start(r1, listOf("a", "b"), 12.0, narrateFirst = false)
        runCurrent()
        assertEquals("start r1-living [a, b] 12 narrated", fake.sent.single())
    }

    /**
     * A video the switch was pressed on for, cast: the speaker narrates it,
     * whatever language it speaks, as the phone did.
     */
    @Test
    fun aVideoPressedOnForIsNarratedOnTheSpeakerToo() = runTest {
        val fake = FakeCast()
        store(fake, prefs = Prefs(narration = true)).start(r1, listOf("a"), 12.0, narrateFirst = true)
        runCurrent()
        assertEquals(listOf("start r1-living [a] 12 narrated", "narration true"), fake.sent)
    }

    @Test
    fun theNarrationSwitchShowsAtOnceAndAsksTheSpeaker() = runTest {
        val fake = FakeCast()
        val store = store(fake)
        store.openRemote("s1")
        runCurrent()
        store.narration(true)
        val shown = (store.state.value.remote as CastRemote.Showing).session
        assertEquals(CastNarration.Preparing, shown.narration, "lit at once, not after the round trip")
        runCurrent()
        assertTrue("narration true" in fake.sent, fake.sent.toString())
    }

    /** The other half: the phone's switch off wins over a speaker narrating. */
    @Test
    fun aVideoPickedWithThePhonesNarrationOffIsNotNarrated() = runTest {
        val fake = FakeCast()
        fake.running = listOf(session(narration = CastNarration.On))
        fake.remote = CastAnswer.Done(session(narration = CastNarration.On))
        val store = store(fake, prefs = Prefs(narration = false))
        store.setForeground(true)
        runCurrent()
        assertTrue(store.pick(listOf("z", "y")))
        runCurrent()
        assertTrue("start r1-living [z, y] 0" in fake.sent, fake.sent.toString())
    }

    /**
     * "bật cho watch thì ko đồng nghĩa đã bật cho thuyết minh": one switch.
     * The speaker's is the phone's preference, both ways.
     */
    @Test
    fun theSpeakersNarrationSwitchIsThePhones() = runTest {
        val prefs = Prefs(narration = false)
        val store = store(FakeCast(), prefs = prefs)
        store.openRemote("s1")
        runCurrent()
        store.narration(true)
        runCurrent()
        assertTrue(prefs.narration, "switched on at the speaker, still off for the phone")
        store.narration(false)
        runCurrent()
        assertFalse(prefs.narration, "switched off at the speaker, still on for the phone")
    }

    @Test
    fun aVideoPickedWhileTheSpeakerPlaysNarratesAsThePhoneWould() = runTest {
        val fake = FakeCast()
        fake.running = listOf(session(narration = CastNarration.Off))
        fake.remote = CastAnswer.Done(session(narration = CastNarration.Off))
        val store = store(fake, prefs = Prefs(narration = true))
        store.setForeground(true)
        runCurrent()
        assertTrue(store.pick(listOf("z", "y")))
        runCurrent()
        assertTrue("start r1-living [z, y] 0 narrated" in fake.sent, "narration on for the phone, off for the speaker: ${fake.sent}")
    }

    @Test
    fun aSpeakerTakenSaysSoAndCloses() = runTest {
        val fake = FakeCast()
        fake.remote = CastAnswer.Done(session(CastState.Ended, CastEnd.TakenOver))
        val store = store(fake)
        store.openRemote("s1")
        runCurrent()
        assertEquals(CastRemote.Closed, store.state.value.remote)
        assertEquals(CastNotice.Ended(CastEnd.TakenOver), store.state.value.notice)
    }

    @Test
    fun stoppingItYourselfClosesWithoutAWord() = runTest {
        val fake = FakeCast()
        fake.remote = CastAnswer.Done(session(CastState.Ended, CastEnd.Stopped))
        val store = store(fake)
        store.openRemote("s1")
        runCurrent()
        assertEquals(CastRemote.Closed, store.state.value.remote)
        assertEquals(CastNotice.None, store.state.value.notice)
    }

    @Test
    fun aSongDraggedPastThePlayingOneKeepsThePlayingOnePlaying() = runTest {
        val fake = FakeCast()
        fake.remote = CastAnswer.Done(session(index = 1))
        val store = store(fake)
        store.openRemote("s1")
        runCurrent()
        store.move(0, 2)
        // a, [b], c -> [b], c, a
        val shown = (store.state.value.remote as CastRemote.Showing).session
        assertEquals(listOf("b", "c", "a"), shown.queue.map { it.videoId }, "the new order at once, not after the round trip")
        assertEquals(0, shown.currentIndex)
        runCurrent()
        assertTrue("queue 4 [b, c, a] 0" in fake.sent, fake.sent.toString())
    }

    @Test
    fun aStaleQueueIsReloadedAndSaid() = runTest {
        val fake = FakeCast()
        fake.queueAnswer = CastAnswer.Refused(CastRefusal.StaleQueue)
        val store = store(fake)
        store.openRemote("s1")
        runCurrent()
        val before = fake.sessionCalls
        store.remove(1)
        runCurrent()
        assertEquals(CastNotice.QueueReloaded, store.state.value.notice)
        assertEquals(before + 1, fake.sessionCalls, "reloaded at once, not at the next poll")
    }

    @Test
    fun handingBackClosesAndSaysWhere() = runTest {
        val fake = FakeCast()
        val store = store(fake)
        store.openRemote("s1")
        runCurrent()
        var got: CastHandback? = null
        store.handoff { got = it }
        runCurrent()
        assertEquals(CastHandback("a", 130), got)
        assertEquals(CastRemote.Closed, store.state.value.remote)
    }

    @Test
    fun aSessionAlreadyPlayingWhenTheAppStartsOpensTheRemote() = runTest {
        val fake = FakeCast()
        fake.running = listOf(session())
        val store = store(fake)
        runCurrent()
        assertIs<CastRemote.Showing>(store.state.value.remote, "reopened as it was left: casting")
        assertTrue(store.state.value.casting)
    }

    @Test
    fun nothingPlayingWhenTheAppStartsLeavesTheRemoteClosed() = runTest {
        val store = store(FakeCast())
        runCurrent()
        assertEquals(CastRemote.Closed, store.state.value.remote)
        assertEquals(false, store.state.value.casting)
    }

    @Test
    fun aSessionSeenLaterDoesNotPopTheRemoteOpen() = runTest {
        val fake = FakeCast()
        val store = store(fake)
        runCurrent()
        fake.running = listOf(session())
        advanceTimeBy(5_100)
        assertEquals(CastRemote.Closed, store.state.value.remote, "only at start, not whenever another phone casts")
        assertTrue(store.state.value.casting)
    }

    @Test
    fun pressingCastWhileCastingAsksAndATapOutsideCarriesOn() = runTest {
        val fake = FakeCast()
        val store = store(fake)
        runCurrent()
        fake.running = listOf(session())
        advanceTimeBy(5_100)
        store.askAboutCasting()
        assertTrue(store.state.value.asking)
        store.dismissAsking()
        runCurrent()
        assertEquals(false, store.state.value.asking)
        assertTrue(fake.sent.isEmpty(), "carrying on sends the speaker nothing: ${fake.sent}")
        assertTrue(store.state.value.casting)
    }

    @Test
    fun pressingCastWhileCastingAsksAndCanStopHere() = runTest {
        val fake = FakeCast()
        val store = store(fake)
        runCurrent()
        fake.running = listOf(session())
        advanceTimeBy(5_100)
        store.askAboutCasting()
        var back: CastHandback? = null
        store.stopCasting { back = it }
        runCurrent()
        assertEquals(CastHandback("a", 130), back)
        assertEquals(false, store.state.value.asking)
        assertEquals(false, store.state.value.casting)
    }

    @Test
    fun aVideoPickedWhileCastingGoesToTheSameSpeakerAndShowsTheRemote() = runTest {
        val fake = FakeCast()
        val store = store(fake)
        runCurrent()
        fake.running = listOf(session())
        advanceTimeBy(5_100)
        assertTrue(store.pick(listOf("x", "y")), "the speaker took it, so the phone opens nothing")
        assertEquals("x", store.state.value.pending, "the speaker still reports the old song until it answers")
        runCurrent()
        assertEquals("", store.state.value.pending)
        assertTrue("start r1-living [x, y] 0" in fake.sent, fake.sent.toString())
        assertIs<CastRemote.Showing>(store.state.value.remote, "the remote, not the watch screen")
    }

    // Reported: profile A casts to a speaker, the phone switches to profile B,
    // and B's next video went to A's speaker. Each profile casts on its own.
    @Test
    fun anotherProfileDoesNotInheritTheCastOfTheOneBefore() = runTest {
        val fake = FakeCast()
        val store = store(fake)
        runCurrent()
        store.start(r1, listOf("a"), 0.0, narrateFirst = false)
        runCurrent()
        assertTrue(store.state.value.casting)

        fake.running = emptyList()
        store.profileChanged()
        runCurrent()

        assertFalse(store.pick(listOf("x")), "profile B's video opens on the phone")
        assertFalse(store.state.value.casting, "B's cast button is not lit by A's speaker")
        assertEquals(CastRemote.Closed, store.state.value.remote)
        assertTrue(fake.sent.none { it.startsWith("start r1-living [x") }, fake.sent.toString())
    }

    @Test
    fun aVideoPickedAloneWhileCastingTakesItsUpNextWithIt() = runTest {
        val fake = FakeCast()
        val store = store(fake, upNext = listOf("y", "x", "z"))
        runCurrent()
        fake.running = listOf(session())
        advanceTimeBy(5_100)
        store.pick(listOf("x"))
        runCurrent()
        assertTrue("start r1-living [x, y, z] 0" in fake.sent, fake.sent.toString())
    }

    @Test
    fun theSongAlreadyPlayingPickedAgainOnlyShowsTheRemote() = runTest {
        val fake = FakeCast()
        val store = store(fake)
        runCurrent()
        fake.running = listOf(session())
        advanceTimeBy(5_100)
        assertTrue(store.pick(listOf("a", "b")))
        runCurrent()
        assertTrue(fake.sent.none { it.startsWith("start") }, fake.sent.toString())
        assertIs<CastRemote.Showing>(store.state.value.remote)
    }

    @Test
    fun aVideoPickedWithNothingPlayingIsThePhones() = runTest {
        val fake = FakeCast()
        val store = store(fake)
        runCurrent()
        assertEquals(false, store.pick(listOf("x")))
        runCurrent()
        assertTrue(fake.sent.none { it.startsWith("start") }, fake.sent.toString())
        assertEquals(CastRemote.Closed, store.state.value.remote)
    }

    @Test
    fun theBarsPlayPauseReachesTheSpeakerWithTheRemoteClosed() = runTest {
        val fake = FakeCast()
        val store = store(fake)
        runCurrent()
        fake.running = listOf(session())
        advanceTimeBy(5_100)
        store.pause()
        store.stop()
        runCurrent()
        assertEquals(listOf("pause", "stop"), fake.sent)
    }
}
