package com.mytube.app.ui.cast

import com.mytube.app.domain.model.CastAnswer
import com.mytube.app.domain.model.CastDevice
import com.mytube.app.domain.model.CastDeviceKind
import com.mytube.app.domain.model.CastEnd
import com.mytube.app.domain.model.CastHandback
import com.mytube.app.domain.model.CastItem
import com.mytube.app.domain.model.CastRefusal
import com.mytube.app.domain.model.CastSession
import com.mytube.app.domain.model.CastState
import com.mytube.app.domain.repository.CastRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val r1 = CastDevice("r1-living", "Loa R1", CastDeviceKind.R1)

private fun session(
    state: CastState = CastState.Playing,
    end: CastEnd = CastEnd.Running,
    index: Int = 0,
) = CastSession(
    id = "s1", device = r1, state = state, end = end,
    queue = listOf("a", "b", "c").map { CastItem(it, "Song ${it.uppercase()}", "Chan", "", 200) },
    currentIndex = index, positionSeconds = 30, durationSeconds = 200, volume = 0, version = 4,
)

private class FakeCast : CastRepository {
    var sessionCalls = 0
    var sessionsCalls = 0
    var remote: CastAnswer<CastSession> = CastAnswer.Done(session())
    var queueAnswer: CastAnswer<CastSession> = CastAnswer.Done(session())
    val sent = mutableListOf<String>()

    override suspend fun devices() = listOf(r1)
    override suspend fun sessions(): CastAnswer<List<CastSession>> {
        sessionsCalls++
        return CastAnswer.Done(emptyList())
    }
    override suspend fun session(id: String): CastAnswer<CastSession> {
        sessionCalls++
        return remote
    }
    override suspend fun start(deviceId: String, videoIds: List<String>, startPositionSeconds: Int): CastAnswer<CastSession> {
        sent += "start $deviceId $videoIds $startPositionSeconds"
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
    override suspend fun replaceQueue(id: String, version: Int, videoIds: List<String>, currentIndex: Int): CastAnswer<CastSession> {
        sent += "queue $version $videoIds $currentIndex"
        return queueAnswer
    }
    override suspend fun handoff(id: String): CastAnswer<CastHandback> {
        sent += "handoff"
        return CastAnswer.Done(CastHandback("a", 130))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CastStoreTest {

    private fun TestScope.store(fake: FakeCast) = CastStore(fake, backgroundScope)

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
        store.start(r1, listOf("a", "b"), 89.6)
        runCurrent()
        assertEquals("start r1-living [a, b] 90", fake.sent.single())
        assertIs<CastRemote.Showing>(store.state.value.remote)
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
    fun movingASongUpKeepsThePlayingOneWhereItIs() = runTest {
        val fake = FakeCast()
        fake.remote = CastAnswer.Done(session(index = 1))
        val store = store(fake)
        store.openRemote("s1")
        runCurrent()
        store.moveUp(2)
        runCurrent()
        // a, [b], c -> a, c, [b]
        assertTrue("queue 4 [a, c, b] 2" in fake.sent, fake.sent.toString())
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
}
