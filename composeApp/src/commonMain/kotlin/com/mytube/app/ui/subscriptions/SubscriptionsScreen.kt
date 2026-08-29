package com.mytube.app.ui.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mytube.app.domain.model.Channel
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.home.formatCount
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.i18n.VietnameseStrings
import com.mytube.app.ui.shell.EmptyState
import com.mytube.app.ui.shell.ScreenTitle
import com.mytube.app.ui.shell.TabScaffold
import com.mytube.app.ui.shell.tabContentPadding
import com.mytube.app.ui.theme.MytubeTheme
import com.mytube.app.ui.theme.Tokens
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The channels this member follows.
 *
 * A list of channels, not of their uploads. New uploads from followed channels
 * already have a fixed share of Home — the charter's `slotFreshSubscribed` — so
 * repeating them here would be a second feed with the same contents and no way
 * to tell the two apart. What this screen is for is *reaching a channel*.
 */
@Composable
fun SubscriptionsScreen(
    viewModel: SubscriptionsViewModel,
    mediaBaseUrl: String,
    onOpenSettings: () -> Unit,
    onOpenChannel: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SubscriptionsContent(
        state = state,
        mediaBaseUrl = mediaBaseUrl,
        onOpenSettings = onOpenSettings,
        onOpenChannel = onOpenChannel,
        onRefresh = viewModel::refresh,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsContent(
    state: SubscriptionsState,
    mediaBaseUrl: String,
    onOpenSettings: () -> Unit,
    onOpenChannel: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    val strings = LocalStrings.current

    TabScaffold(
        loading = state is SubscriptionsState.Loading,
        needsServer = state is SubscriptionsState.NeedsServer,
        failure = (state as? SubscriptionsState.Failed)?.message.orEmpty(),
        noServerTitle = strings.noServerTitle,
        setTheAddress = strings.setTheAddress,
        couldNotReach = strings.couldNotReach,
        tryAgain = strings.tryAgain,
        onOpenSettings = onOpenSettings,
        onRetry = onRefresh,
    ) {
        val ready = state as? SubscriptionsState.Ready ?: return@TabScaffold

        PullToRefreshBox(
            isRefreshing = ready.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
            indicator = {},
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = tabContentPadding(),
            ) {
                item(key = "title") { ScreenTitle(strings.subscriptionsTitle) }

                if (ready.channels.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(strings.noSubscriptions, strings.noSubscriptionsDetail)
                    }
                }

                items(ready.channels, key = { it.id }) { channel ->
                    ChannelRow(channel, mediaBaseUrl, onClick = { onOpenChannel(channel.id) })
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(channel: Channel, mediaBaseUrl: String, onClick: () -> Unit) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = "$mediaBaseUrl/media/${channel.avatarPath}",
            contentDescription = channel.name,
            contentScale = ContentScale.Crop,
            // 48, larger than the 40 on the watch page and the 36 on a card: here
            // the avatar is the thing being chosen from, not a label beside a
            // title.
            modifier = Modifier.size(48.dp).clip(CircleShape).background(Tokens.surface),
        )
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = channel.name,
                color = Tokens.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Only when the server sent one. A channel reading "0 subscribers"
            // is stating a fact nobody measured — the count is absent on most
            // rows here, because a flat listing does not carry it.
            if (channel.subscriberCount > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${formatCount(channel.subscriberCount, strings)} ${strings.subscribers}",
                    color = Tokens.text2,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

// --- previews ---------------------------------------------------------------

private fun sample(id: String, name: String, subscribers: Long) = Channel(
    id = id,
    name = name,
    handle = "@$id",
    avatarPath = "channels/$id/avatar.jpg",
    subscribed = true,
    subscriberCount = subscribers,
)

@Preview
@Composable
private fun SubscriptionsPreview() {
    MytubeTheme {
        SubscriptionsContent(
            state = SubscriptionsState.Ready(
                listOf(
                    sample("cbc", "CBC News", 4_100_000),
                    // No count, which is the ordinary case: a flat listing does
                    // not carry one, so the row must read correctly without it.
                    sample("relab", "relab", 0),
                    sample("tested", "Adam Savage's Tested", 6_200_000),
                ),
            ),
            mediaBaseUrl = "",
            onOpenSettings = {},
            onOpenChannel = {},
            onRefresh = {},
        )
    }
}

@Preview
@Composable
private fun SubscriptionsEmptyPreview() {
    MytubeTheme {
        SubscriptionsContent(
            state = SubscriptionsState.Ready(emptyList()),
            mediaBaseUrl = "",
            onOpenSettings = {},
            onOpenChannel = {},
            onRefresh = {},
        )
    }
}

@Preview
@Composable
private fun SubscriptionsVietnamesePreview() {
    CompositionLocalProvider(LocalStrings provides VietnameseStrings) {
        MytubeTheme {
            SubscriptionsContent(
                state = SubscriptionsState.Ready(listOf(sample("cbc", "CBC News", 4_100_000))),
                mediaBaseUrl = "",
                onOpenSettings = {},
                onOpenChannel = {},
                onRefresh = {},
            )
        }
    }
}
