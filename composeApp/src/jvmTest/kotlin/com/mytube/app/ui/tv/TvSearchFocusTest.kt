package com.mytube.app.ui.tv

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.Video
import com.mytube.app.screenshot.coilReady
import com.mytube.app.ui.search.SearchState
import com.mytube.app.ui.shell.LocalTelevision
import com.mytube.app.ui.theme.MytubeTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Search pressed on the TV's keyboard sends focus across to the list, and the
 * list it lands on can be replaced under it: the suggestions by the first
 * answer, an answer by a refined one, the failure by a retry's answer. Focus
 * stays in the list; it used to go with the rows, and from there to the rail.
 */
@OptIn(ExperimentalComposeUiApi::class)
class TvSearchFocusTest {

    private fun rows(prefix: String) = List(12) {
        Video(
            id = "$prefix$it", title = "$prefix row $it",
            channel = Channel(id = "c", name = "Channel", handle = "", avatarPath = ""),
            durationSeconds = 60, viewCount = 1, publishedAt = "", thumbnailPath = "",
        )
    }

    @Test
    fun focusStaysInTheListWhenTheAnswerReplacesIt() {
        coilReady
        val width = 1920
        var state by mutableStateOf<SearchState>(SearchState.Idle)
        val scene = ImageComposeScene(width = width, height = 1080, density = Density(2f)) {
            CompositionLocalProvider(LocalTelevision provides true) {
                MytubeTheme {
                    TvSearchContent(
                        query = "iphone", state = state, suggestions = rows("suggested"), asking = true,
                        opening = "", openFailed = false, mediaBaseUrl = "", startLayout = KeyboardLayout.Latin, voice = null,
                        onType = {}, onRetry = {}, onOpen = {}, onMore = {}, onNotInterested = {},
                        onSaveToPlaylist = {},
                    )
                }
            }
        }
        var t = 0L
        fun frames(n: Int) = repeat(n) { scene.render(t); t += 16_000_000L }
        fun nodes() = scene.semanticsOwners.flatMap { it.unmergedRootSemanticsNode.descendants() }
        fun focusInList() = nodes().any {
            it.config.getOrNull(SemanticsProperties.Focused) == true && it.boundsInRoot.left > width / 2f
        }
        try {
            frames(5)
            val search = nodes().first { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Search" } == true
            }.boundsInRoot.center
            scene.sendPointerEvent(PointerEventType.Press, Offset(search.x, search.y), timeMillis = t / 1_000_000)
            frames(1)
            scene.sendPointerEvent(PointerEventType.Release, Offset(search.x, search.y), timeMillis = t / 1_000_000)
            frames(5)
            assertTrue(focusInList(), "Search sends focus to the suggestions")

            state = SearchState.Ready("iphone", rows("answer"))
            frames(10)
            assertTrue(focusInList(), "the answer replaced the suggestions and focus left the list")

            state = SearchState.Ready("iphone 17", rows("refined"))
            frames(10)
            assertTrue(focusInList(), "a refined answer replaced the first and focus left the list")
        } finally {
            scene.close()
        }
    }

    private fun SemanticsNode.descendants(): List<SemanticsNode> =
        listOf(this) + children.flatMap { it.descendants() }
}
