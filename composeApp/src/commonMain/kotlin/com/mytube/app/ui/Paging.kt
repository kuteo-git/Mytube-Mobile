package com.mytube.app.ui

import com.mytube.app.domain.model.Video

/**
 * The next page, minus anything already on the page.
 *
 * **A duplicate row is a crash, not a repetition.** Every feed in this app is
 * a `LazyColumn` keyed on the video id, and Compose throws out of `subcompose`
 * during measure when one key arrives twice — which reaches the phone as an
 * unhandled Kotlin exception and `SIGABRT`. Measured on 2026-09-06, scrolling
 * the Missed chip: `LazyListMeasuredItemProvider.getAndMeasure` → `subcompose`
 * in `lastExceptionBacktrace`.
 *
 * And a duplicate is not a fault to be fixed at the other end: these endpoints
 * page by **offset**, so a video ingested between two requests shifts the
 * window and hands back a row that is already on screen. That is ordinary and
 * the server is not wrong to do it. What the client cannot do is promise
 * unique keys it has not checked.
 *
 * The row already shown is the one kept — it is what somebody may be looking
 * at, and replacing it would move the list under them for no gain.
 */
fun List<Video>.appendNew(page: List<Video>): List<Video> = appendNewBy(page) { it.id }

/**
 * A page as it must enter a keyed list: the first of each id kept, the rest
 * dropped.
 *
 * The **first** page needs this as much as a later one. [appendNew] dedups a
 * page against what is already shown, but the first page has nothing to append
 * to, so a repeat *within* it slips through — and a playlist genuinely lists the
 * same video twice, besides the offset-paging overlap [appendNew] documents. A
 * `LazyColumn` keyed on the id SIGABRTs on the repeat whether it arrived in page
 * one or page five, so the first page dedups against itself. Measured on
 * 2026-11-24, scrolling a channel playlist's first page.
 */
fun List<Video>.distinctById(): List<Video> = emptyList<Video>().appendNew(this)

/**
 * [appendNew] for any keyed row, so a channel's playlists dedup the same way its
 * videos do: a `LazyColumn` keyed on a repeated id is the same `SIGABRT`,
 * whatever the rows are.
 */
fun <T> List<T>.appendNewBy(page: List<T>, id: (T) -> String): List<T> {
    val seen = mapTo(mutableSetOf()) { id(it) }
    return this + page.filter { seen.add(id(it)) }
}
