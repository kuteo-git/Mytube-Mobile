package com.mytube.app.domain.repository

import com.mytube.app.domain.model.StoryboardState

/**
 * The scrub-preview sheets for one video.
 *
 * Its own port, and not a second method on [StreamRepository]. That one answers
 * "how can this be played" — a question asked at the moment somebody presses
 * play, which may involve talking to YouTube and can answer differently a minute
 * later. This asks what a *control* can draw, it is asked once per video, and its
 * answer never changes: the sheets are files copied to disk and the geometry
 * describing them is fixed for ever. Folding it in would leave that interface's
 * name true of half its members, which is the reasoning §3 records for keeping
 * `ServerRepository` to one question.
 *
 * Nor is it on [NarrationRepository], the other on-demand server asset: that one
 * drives a job — it starts work and is polled while the work runs. This is a
 * single answer.
 */
interface StoryboardRepository {

    /**
     * Ask for a video's preview sheets.
     *
     * Never throws for a video that has none. Most of this library has none —
     * Shorts too brief for a ladder, and anything upstream will not discuss — so
     * absence is [StoryboardState.None] rather than an exception a caller has to
     * catch to stay quiet. A failure to reach the server is also `None`: the page
     * is whole without a preview, and the same judgement is already made about
     * comments and about a description that would not load.
     */
    suspend fun storyboard(videoId: String): StoryboardState
}
