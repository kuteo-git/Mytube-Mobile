package com.mytube.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytube.app.domain.model.Profile
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.home.avatarColourFor
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.i18n.VietnameseStrings
import com.mytube.app.ui.shell.DetailBack
import com.mytube.app.ui.shell.ScreenTitle
import com.mytube.app.ui.shell.detailContentPadding
import com.mytube.app.ui.shell.glassSource
import com.mytube.app.ui.theme.MytubeTheme
import com.mytube.app.ui.theme.Tokens
import androidx.compose.runtime.CompositionLocalProvider
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Who this device is watching as.
 *
 * ## Why this is a picker and not a login
 *
 * The server charter is explicit: *"a profile picker, not a login"*. Two to five
 * people on one wifi, with media URLs deliberately unprotected — a password here
 * would be a stricter promise than the system keeps, guarding metadata about
 * videos anyone on the LAN can already fetch.
 *
 * ## Why it is a page in Settings and not the avatar in the bar
 *
 * It was a sheet, opened from an avatar in the top bar's right corner — where
 * YouTube puts it, and where it cost this app the most-looked-at corner of every
 * screen for a question a household of four answers about once a month. As a row
 * it also states its own answer: Settings reads the member's name without
 * anybody pressing anything.
 *
 * The one thing lost with the sheet is that it drew nothing for a household of
 * one. A row cannot do that without lying about what Settings contains, and it
 * does not need to: a list with one ticked row still answers "who am I watching
 * as", which is the question somebody opening this asked.
 *
 * ## What is deliberately not here
 *
 * The web's menu also offers *Manage profiles* and *YouTube account*. Creating a
 * profile, deleting one, and pasting a cookies.txt are all things done sitting
 * at the machine — the last especially, since the file comes from a browser
 * extension. Drawing them here as rows that open nothing is the dead button the
 * charter forbids.
 */
@Composable
fun ProfileScreen(
    profiles: List<Profile>,
    currentId: String,
    onPick: (Profile) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current

    Surface(color = Tokens.bg, modifier = Modifier.fillMaxSize()) {
        // Drawn the way Saved and History are — the heading as the list's first
        // item, the arrow over it — rather than as a `DetailScaffold`, whose
        // title sits in a row of its own. Both shapes were in the app and the
        // difference between them was not a decision anybody had made.
        //
        // Recorded, so the miniplayer floating over this page is glass here too.
        Box(Modifier.fillMaxSize().glassSource()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = detailContentPadding()) {
                item(key = "title") { ScreenTitle(strings.settingsProfile) }

                items(profiles, key = { it.id }) { profile ->
                    ProfileRow(
                        profile = profile,
                        selected = profile.id == currentId,
                        onClick = { onPick(profile) },
                    )
                }
            }

            DetailBack(onBack, strings.back)
        }
    }
}

@Composable
private fun ProfileRow(profile: Profile, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Coloured from the name, the way every avatar in this app is. A grey
        // circle reads as a picture that has not loaded; a coloured one reads as
        // a person.
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(avatarColourFor(profile.name)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = profile.name.take(1).uppercase(),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.width(Space.md))
        Text(profile.name, color = Tokens.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        // The same tick as the language list, for the same reason: one mark on
        // the chosen row and nothing on the others, which is how the platform's
        // own Settings answers a question of this shape.
        if (selected) {
            Icon(
                imageVector = TickIcon,
                contentDescription = null,
                tint = Tokens.brand,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// --- previews ---------------------------------------------------------------

private val household = listOf(
    Profile(id = "1", name = "Luc"),
    Profile(id = "2", name = "Mai"),
    Profile(id = "3", name = "Bin"),
)

@Preview
@Composable
private fun ProfilePreview() {
    MytubeTheme {
        ProfileScreen(profiles = household, currentId = "2", onPick = {}, onBack = {})
    }
}

/** A household of one, which is a list of one and still an answer. */
@Preview
@Composable
private fun ProfileAlonePreview() {
    MytubeTheme {
        ProfileScreen(
            profiles = household.take(1),
            currentId = "1",
            onPick = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun ProfileVietnamesePreview() {
    MytubeTheme {
        CompositionLocalProvider(LocalStrings provides VietnameseStrings) {
            ProfileScreen(profiles = household, currentId = "1", onPick = {}, onBack = {})
        }
    }
}
