package com.mytube.app.data

import com.mytube.app.data.repository.channelToken
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which token a channel listing is asked for, and the fault it exists to stop.
 *
 * The app sent the ordering as `sort=` beside a `pageToken`, on the reasoning
 * that picking an order and continuing within one are two different jobs. They
 * are — and the gateway reads only `pageToken`, so `sort=` was dropped in
 * silence: pressing Popular did the request, changed nothing, and looked like a
 * dead control. Measured against the running server on
 * `UCsT0YIqwnpJCM-mx7-gSA4Q`, where the two orderings genuinely differ:
 * `?pageToken=<popular>` answers `GNZBSZD16cY…`, `?sort=<popular>` answers the
 * Latest list.
 *
 * Nothing in the type system catches a query parameter nobody reads — both are
 * strings, and both requests succeed. So this does.
 */
class ChannelTokenTest {

    private val popular = "4qmFsgJkEhhVQ3NUMFlJcXduSkNNLW14Ny1nU0E0UQ"
    private val cursor = "4qmFsgKPARIYVUNzVDBZSXF3bkpDTS1teDctZ1NBNFEac"

    @Test
    fun theDefaultOrderAsksForNothing() {
        // An empty token is left off the request entirely rather than sent, or
        // the server is asked to parse "" as a continuation.
        assertEquals("", channelToken(sortToken = "", pageToken = ""))
    }

    @Test
    fun anOrderingIsAskedForAsAContinuation() {
        // This is the whole fix. YouTube models an ordering as a continuation
        // like any other, and the web app has always sent it as the first page
        // token.
        assertEquals(popular, channelToken(sortToken = popular, pageToken = ""))
    }

    @Test
    fun aCursorWinsOverTheOrderingItCameFrom() {
        // The cursor already carries the ordering it was handed out inside —
        // measured: page two of Popular is page two *of Popular*. Sending the
        // ordering again would ask for its first page and append it to itself.
        assertEquals(cursor, channelToken(sortToken = popular, pageToken = cursor))
    }
}
