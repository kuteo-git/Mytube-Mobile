package com.mytube.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.Video
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.i18n.Strings
import com.mytube.app.ui.theme.MytubeTheme
import com.mytube.app.ui.theme.Tokens
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.roundToLong

/**
 * The feed.
 *
 * A single column of large cards, which is what the web app's phone layout does
 * and what the shape of the content wants: a thumbnail is the only thing anybody
 * reads at a glance, and two per row on a phone makes it too small to be that.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    mediaBaseUrl: String,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    HomeContent(
        state = state,
        mediaBaseUrl = mediaBaseUrl,
        onOpenSettings = onOpenSettings,
        onRetry = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
    )
}

/**
 * The feed, as plain values.
 *
 * Split from [HomeScreen] so it can be previewed and rendered in a test without
 * a ViewModel — the same split every screen here uses.
 */
@Composable
fun HomeContent(
    state: HomeState,
    mediaBaseUrl: String,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val strings = LocalStrings.current

    Surface(color = Tokens.bg, modifier = Modifier.fillMaxSize()) {
        when (val current = state) {
            is HomeState.Loading -> Centered { CircularProgressIndicator() }

            is HomeState.NeedsServer -> Centered {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(strings.noServerTitle, color = Tokens.text)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onOpenSettings) { Text(strings.setTheAddress) }
                }
            }

            is HomeState.Failed -> Centered {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(strings.couldNotReach, color = Tokens.text)
                    Spacer(Modifier.height(4.dp))
                    Text(current.message, color = Tokens.text2, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onRetry) { Text(strings.tryAgain) }
                }
            }

            is HomeState.Ready -> Feed(current, mediaBaseUrl, strings, onLoadMore)
        }
    }
}

@Composable
private fun Feed(
    state: HomeState.Ready,
    mediaBaseUrl: String,
    strings: Strings,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()

    // Asked when the end comes into view, not when the last item is composed.
    //
    // `derivedStateOf` is what keeps this from recomposing the whole screen on
    // every pixel of scroll: it recomputes on every frame but only *emits* when
    // the boolean flips.
    val atEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.videos.lastIndex - 3
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { atEnd }.collect { if (it) onLoadMore() }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        // The list scrolls under the status bar and the gesture bar, which is
        // what edge-to-edge is for — but its *content* must not start under
        // them. Without this the first thumbnail sits behind the clock, which is
        // exactly what the first run on a device showed.
        contentPadding = WindowInsets.systemBars.asPaddingValues(),
    ) {
        items(state.videos, key = { it.id }) { video ->
            VideoCard(video, mediaBaseUrl, strings)
        }
        if (state.loadingMore) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun VideoCard(video: Video, mediaBaseUrl: String, strings: Strings) {
    Column(modifier = Modifier.fillMaxWidth().clickable { }.padding(bottom = 16.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Tokens.surface)) {
            AsyncImage(
                model = "$mediaBaseUrl/media/${video.thumbnailPath}",
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
            if (video.durationSeconds > 0) {
                Text(
                    text = formatDuration(video.durationSeconds),
                    color = Tokens.text,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        // The background is the whole point of the badge, and it
                        // was missing: white text alone is unreadable over a
                        // bright thumbnail, which is most of them. Caught by
                        // rendering the screen rather than by reading the code.
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }

        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            AsyncImage(
                model = "$mediaBaseUrl/media/${video.channel.avatarPath}",
                contentDescription = video.channel.name,
                contentScale = ContentScale.Crop,
                // A circle of surface colour while it loads, so the title does
                // not shift sideways when the avatar arrives.
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Tokens.surface),
            )
            Spacer(Modifier.size(12.dp))
            Column {
                Text(
                    text = video.title,
                    color = Tokens.text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${video.channel.name} · ${formatViews(video.viewCount, strings)}",
                    color = Tokens.text2,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/**
 * `12:34`, and `1:02:03` past an hour.
 *
 * No language parameter, and that is not an oversight: a duration is digits and
 * colons in both languages this app speaks. The moment one of them wants
 * something else, it takes a parameter like the others.
 */
fun formatDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val mm = if (hours > 0) minutes.toString().padStart(2, '0') else minutes.toString()
    return if (hours > 0) "$hours:$mm:${seconds.toString().padStart(2, '0')}"
    else "$mm:${seconds.toString().padStart(2, '0')}"
}

/**
 * `4.1M` in English, `4.1Tr` in Vietnamese.
 *
 * The suffix comes from [Strings] rather than a table in here, because that is
 * precisely where the web app's version went wrong — its formatters carried
 * English grammar into a language with no plural and printed "3 ngàys trước".
 * The unit belongs to the language, so the language supplies it.
 *
 * The rounding rule is the web app's, so both clients abbreviate the same
 * number the same way: one decimal below a hundred, none at or above it.
 */
fun formatCount(n: Long, strings: Strings): String = when {
    n >= 1_000_000_000 -> trim(n / 1_000_000_000.0) + strings.billionSuffix
    n >= 1_000_000 -> trim(n / 1_000_000.0) + strings.millionSuffix
    n >= 1_000 -> trim(n / 1_000.0) + strings.thousandSuffix
    else -> n.toString()
}

/** `157K views` / `157N lượt xem`. */
fun formatViews(n: Long, strings: Strings): String =
    "${formatCount(n, strings)} ${strings.views}"

private fun trim(value: Double): String {
    if (value >= 100) return value.roundToLong().toString()
    val oneDecimal = (value * 10).roundToLong() / 10.0
    val whole = oneDecimal.toLong()
    return if (oneDecimal == whole.toDouble()) whole.toString() else oneDecimal.toString()
}

// --- previews ---------------------------------------------------------------
//
// Every state, not just the one that works. The empty and failed cases are the
// ones a person meets when something is wrong, which is precisely when a badly
// laid out screen is least welcome — and they are the states hardest to reach on
// a device, so they are the ones most likely to go unseen without these.

private fun sampleVideo(id: String, title: String, seconds: Int, views: Long) = Video(
    id = id,
    title = title,
    channel = Channel(
        id = "c",
        name = "CBC News",
        handle = "@CBCNews",
        avatarPath = "channels/c/avatar.jpg",
        subscribed = true,
    ),
    durationSeconds = seconds,
    viewCount = views,
    publishedAt = "2026-08-28T05:35:52Z",
    thumbnailPath = "thumbnails/$id.jpg",
)

@Preview
@Composable
private fun HomeReadyPreview() = MytubeTheme {
    HomeContent(
        state = HomeState.Ready(
            videos = listOf(
                sampleVideo("a", "Canadian describes what he saw as catastrophic floods hit Nepal", 451, 157_000),
                sampleVideo("b", "A short one", 42, 980),
                sampleVideo("c", "Something over an hour long, with a title that runs on and on and has to be cut", 4_231, 4_730_000),
            ),
            nextPageToken = "t1",
        ),
        mediaBaseUrl = "",
        onOpenSettings = {},
        onRetry = {},
        onLoadMore = {},
    )
}

@Preview
@Composable
private fun HomeNeedsServerPreview() = MytubeTheme {
    HomeContent(HomeState.NeedsServer, "", {}, {}, {})
}

@Preview
@Composable
private fun HomeFailedPreview() = MytubeTheme {
    HomeContent(HomeState.Failed("gateway answered 502"), "", {}, {}, {})
}

@Preview
@Composable
private fun HomeLoadingPreview() = MytubeTheme {
    HomeContent(HomeState.Loading, "", {}, {}, {})
}
