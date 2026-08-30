package com.mytube.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.ModalBottomSheet
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
import com.mytube.app.ui.theme.Tokens

/**
 * Who is watching, chosen from the avatar in the top bar.
 *
 * ## Why this is a picker and not a login
 *
 * The server charter is explicit: *"a profile picker, not a login"*. Two to five
 * people on one wifi, with media URLs deliberately unprotected — a password here
 * would be a stricter promise than the system keeps, guarding metadata about
 * videos anyone on the LAN can already fetch.
 *
 * ## What is deliberately not here
 *
 * The web's menu also offers *Manage profiles* and *YouTube account*. Creating a
 * profile, deleting one, and pasting a cookies.txt are all things done sitting
 * at the machine — the last especially, since the file comes from a browser
 * extension. Drawing them here as rows that open nothing is the dead button the
 * charter forbids; leaving them out says truthfully that this app switches
 * between the people who already exist.
 *
 * ## Why nothing is drawn for a household of one
 *
 * A picker with one row asks a question that has one answer. The avatar simply
 * does nothing until a second member exists, which is the same rule the web app
 * follows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(
    profiles: List<Profile>,
    currentId: String,
    onPick: (Profile) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    // Material's, blurred — see [SheetBackdrop].
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        dragHandle = null,
    ) {
        Box {
        SheetBackdrop(Modifier.matchParentSize())
        Column(Modifier.fillMaxWidth().padding(bottom = Space.xxl)) {
            Text(
                text = strings.profileTitle,
                color = Tokens.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.md),
            )
            profiles.forEach { profile ->
                ProfileRow(
                    profile = profile,
                    selected = profile.id == currentId,
                    onClick = { onPick(profile) },
                )
            }
        }
        }
    }
}

@Composable
private fun ProfileRow(profile: Profile, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) Tokens.surfaceHover else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        Column {
            Text(profile.name, color = Tokens.text, fontSize = 14.sp)
            if (selected) {
                Spacer(Modifier.height(2.dp))
                Text(LocalStrings.current.profileCurrent, color = Tokens.text2, fontSize = 12.sp)
            }
        }
    }
}
