package com.mytube.app.ui

import com.mytube.app.domain.repository.ServerRepository
import com.mytube.app.ui.settings.CheckState
import com.mytube.app.ui.settings.ServerSetupViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ServerSetupViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun offersTheAddressAlreadySaved() = runTest(dispatcher) {
        // Somebody opening the settings to fix a typo should see what is there,
        // not an empty box that makes them find the address again.
        val model = ServerSetupViewModel(FakeServer(saved = "http://10.0.0.5:8180"))
        testScheduler.advanceUntilIdle()

        assertEquals("http://10.0.0.5:8180", model.state.value.address)
    }

    @Test
    fun editingClearsThePreviousVerdict() = runTest(dispatcher) {
        // A green tick beside a half-edited address is the screen lying about
        // the one thing it exists to confirm.
        val model = ServerSetupViewModel(FakeServer(reachable = true))
        model.onAddressChanged("10.0.0.5:8180")
        model.check()
        testScheduler.advanceUntilIdle()
        assertEquals(CheckState.Reachable, model.state.value.check)

        model.onAddressChanged("10.0.0.5:818")

        assertEquals(CheckState.Untested, model.state.value.check)
    }

    @Test
    fun anUnreachableAddressIsNamedRatherThanSilent() = runTest(dispatcher) {
        val model = ServerSetupViewModel(FakeServer(reachable = false))
        model.onAddressChanged("10.0.0.9:8180")
        model.check()
        testScheduler.advanceUntilIdle()

        assertEquals(CheckState.Failed, model.state.value.check)
    }

    @Test
    fun savingDoesNotRequireASuccessfulCheck() = runTest(dispatcher) {
        // The Mac may simply be asleep while somebody sets their phone up.
        // Refusing to remember a correct address because the server is off would
        // be the app being clever at their expense.
        val server = FakeServer(reachable = false)
        val model = ServerSetupViewModel(server)
        model.onAddressChanged("10.0.0.5:8180")
        model.save()
        testScheduler.advanceUntilIdle()

        assertEquals("10.0.0.5:8180", server.saved)
        assertTrue(model.state.value.saved)
    }

    @Test
    fun nothingHappensWithAnEmptyField() = runTest(dispatcher) {
        val server = FakeServer()
        val model = ServerSetupViewModel(server)
        model.onAddressChanged("   ")

        assertFalse(model.state.value.canSubmit)

        model.check()
        model.save()
        testScheduler.advanceUntilIdle()

        assertEquals("", server.saved)
        assertEquals(CheckState.Untested, model.state.value.check)
    }

    private class FakeServer(
        var saved: String = "",
        private val reachable: Boolean = false,
    ) : ServerRepository {
        override suspend fun baseUrl(): String = saved
        override suspend fun setBaseUrl(url: String) { saved = url }
        override suspend fun profileId(): String = ""
        override suspend fun setProfileId(id: String) = Unit
        override suspend fun reachable(url: String): Boolean = reachable
    }
}
