package com.mytube.app.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytube.app.domain.model.Profile
import com.mytube.app.ui.home.ChannelAvatar
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.i18n.VietnameseStrings
import com.mytube.app.ui.shell.FOCUS_MAX_OUTSET
import com.mytube.app.ui.shell.LocalTelevision
import com.mytube.app.ui.shell.pressable
import com.mytube.app.ui.theme.MytubeTheme
import com.mytube.app.ui.theme.Tokens
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Which member of the household is in front of the television.
 *
 * Asked once, which a phone never is: a phone is one person's. A television is
 * the household's, and its history, Continue watching and saved shelf all
 * follow the answer — but asked on every launch it was a question the same
 * person answered every evening. Remembered from then on; the Profile page in
 * Settings is how it changes hands, as on a phone.
 *
 * Shown only when there is more than one member to choose between: a house of
 * one has nothing to answer.
 */
@Composable
fun WhoIsWatchingScreen(
    profiles: List<Profile>,
    /** Who was watching last, focused on arrival. Empty for the first profile. */
    currentId: String,
    onPick: (Profile) -> Unit,
) {
    val strings = LocalStrings.current
    val focus = remember { FocusRequester() }
    val focusedIndex = profiles.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    LaunchedEffect(profiles.size) {
        if (profiles.isNotEmpty()) runCatching { focus.requestFocus() }
    }

    Column(
        Modifier.fillMaxSize().background(Tokens.bg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = strings.whoIsWatching,
            color = Tokens.text,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(Space.xxl))
        LazyRow(
            // Room for a focused avatar's rise on every side, or the row's clip
            // shaves it.
            contentPadding = PaddingValues(FOCUS_MAX_OUTSET),
            horizontalArrangement = Arrangement.spacedBy(Space.xl),
        ) {
            itemsIndexed(profiles, key = { _, profile -> profile.id }) { index, profile ->
                Column(
                    Modifier
                        .width(AVATAR + Space.xl)
                        .then(if (index == focusedIndex) Modifier.focusRequester(focus) else Modifier)
                        .pressable(onClick = { onPick(profile) })
                        .padding(vertical = Space.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ChannelAvatar(
                        name = profile.name,
                        mediaBaseUrl = "",
                        path = "",
                        size = AVATAR,
                    )
                    Spacer(Modifier.height(Space.md))
                    Text(
                        text = profile.name,
                        color = Tokens.text,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * Whether the launch should stop and ask who is watching.
 *
 * A television with more than one member to choose between, that has not yet
 * been told who it is — [chosenId] names nobody in [profiles]: never answered,
 * or the one chosen has since been removed on the server. Nowhere else. A
 * named function because the app's first route is decided from it, and "on a
 * phone this is never shown" is the half nobody would think to check by hand.
 */
internal fun asksWhoIsWatching(television: Boolean, profiles: List<Profile>, chosenId: String): Boolean =
    television && profiles.size > 1 && profiles.none { it.id == chosenId }

private val AVATAR = 96.dp

private val previewHousehold = listOf(Profile("a", "Lan"), Profile("b", "Minh"), Profile("c", "Bà"))

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun WhoIsWatchingPreview() = MytubeTheme {
    CompositionLocalProvider(LocalTelevision provides true) {
        WhoIsWatchingScreen(previewHousehold, "b") {}
    }
}

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun WhoIsWatchingVietnamesePreview() = MytubeTheme {
    CompositionLocalProvider(
        LocalTelevision provides true,
        LocalStrings provides VietnameseStrings,
    ) {
        WhoIsWatchingScreen(previewHousehold, "") {}
    }
}

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun WhoIsWatchingLoadingPreview() = MytubeTheme {
    CompositionLocalProvider(LocalTelevision provides true) {
        Box(Modifier.fillMaxSize()) { WhoIsWatchingScreen(emptyList(), "") {} }
    }
}
