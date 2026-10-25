package com.mytube.app.ui.tv

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rail's tab picked again while it is showing.
 *
 * `request` answers whether anything on the tab took the request, and the rail
 * acts on that answer: false hands focus on to the tab. Saying true with
 * nobody listening would swallow the press — focus left on the rail, nothing
 * scrolled — so the answer has to follow whether a tab is collecting.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReturnToTopTest {

    @Test
    fun `with nothing on the tab answering, a request is refused`() {
        assertFalse(ReturnToTop().request())
    }

    @Test
    fun `a tab that is answering takes the request and acts on it`() = runTest {
        val returns = ReturnToTop()
        var acted = 0
        backgroundScope.launch { returns.collect { acted++ } }
        runCurrent()

        assertTrue(returns.request())
        runCurrent()

        assertEquals(1, acted)
    }

    @Test
    fun `each request is acted on once`() = runTest {
        val returns = ReturnToTop()
        var acted = 0
        backgroundScope.launch { returns.collect { acted++ } }
        runCurrent()

        returns.request()
        runCurrent()
        returns.request()
        runCurrent()

        assertEquals(2, acted)
    }

    /** The tab left composition — Home replaced by a page — and stopped answering. */
    @Test
    fun `a tab that has stopped answering no longer takes requests`() = runTest {
        val returns = ReturnToTop()
        val tab = backgroundScope.launch { returns.collect { } }
        runCurrent()
        assertTrue(returns.request())

        tab.cancel()
        runCurrent()

        assertFalse(returns.request())
    }
}
