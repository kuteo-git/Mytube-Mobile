package com.mytube.app.ui.search

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
import com.mytube.app.ui.theme.MytubeTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Scrolled down one search, then another typed: the new answer is read from its
 * top — "search text khác thì nó vẫn search nhưng offset của scroll vẫn như cũ".
 */
@OptIn(ExperimentalComposeUiApi::class)
class SearchScrollTest {

    private fun answer(prefix: String) = List(40) {
        Video(
            id = "$prefix$it", title = "$prefix result $it",
            channel = Channel(id = "", name = "Channel", handle = "", avatarPath = ""),
            durationSeconds = 60, viewCount = 1, publishedAt = "", thumbnailPath = "",
            sourceUrl = "https://www.youtube.com/watch?v=$prefix$it", inLibrary = false,
        )
    }

    @Test
    fun aNewQueryIsReadFromTheTop() {
        coilReady
        val height = 800 * 3
        var query by mutableStateOf("alpha")
        var state by mutableStateOf<SearchState>(SearchState.Ready("alpha", answer("alpha")))
        val scene = ImageComposeScene(width = 390 * 3, height = height, density = Density(3f)) {
            MytubeTheme {
                SearchContent(
                    state = state, suggestions = emptyList(), asking = false, opening = "",
                    openFailed = false, query = query, mediaBaseUrl = "",
                    onBack = {}, onType = {}, onClear = {}, onRetry = {},
                    onOpen = {}, onLoadMore = {}, onOpenChannel = {}, onNotInterested = {},
                )
            }
        }
        var t = 0L
        fun frames(n: Int) = repeat(n) { scene.render(t); t += 16_000_000L }
        fun onScreen(title: String) = scene.semanticsOwners
            .flatMap { it.unmergedRootSemanticsNode.descendants() }
            .any { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == title } == true &&
                    node.boundsInRoot.bottom > 0f && node.boundsInRoot.top < height
            }
        try {
            frames(5)
            assertTrue(onScreen("alpha result 0"), "the first answer is drawn from its top")
            repeat(40) {
                scene.sendPointerEvent(
                    PointerEventType.Scroll, Offset(600f, 1200f),
                    scrollDelta = Offset(0f, 3f), timeMillis = t / 1_000_000,
                )
                frames(2)
            }
            assertTrue(!onScreen("alpha result 0"), "the first answer scrolled down")

            query = "beta"
            state = SearchState.Ready("beta", answer("beta"))
            frames(5)
            assertTrue(onScreen("beta result 0"), "the new answer opened where the old one was scrolled to")
        } finally {
            scene.close()
        }
    }

    private fun SemanticsNode.descendants(): List<SemanticsNode> =
        listOf(this) + children.flatMap { it.descendants() }
}
