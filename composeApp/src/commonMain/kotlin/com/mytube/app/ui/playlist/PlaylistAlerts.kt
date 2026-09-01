package com.mytube.app.ui.playlist

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.shell.GlassAlert
import com.mytube.app.ui.shell.GlassButton
import com.mytube.app.ui.shell.GlassTextField
import com.mytube.app.ui.theme.Tokens

/**
 * Naming a playlist — making one, or renaming one.
 *
 * One composable for both, because they are one question with two titles. The
 * field is the same, the refusal is the same (an empty name is not a name), and
 * a second copy would be a second place to fix the keyboard.
 *
 * **The keyboard is asked for, not waited for.** Somebody who pressed "+" has
 * already said what they want to do; an alert with a field and no cursor makes
 * them press the field to begin. [GlassAlert] centres itself in what is left of
 * the screen once the keyboard is up, so the field cannot end up behind it.
 */
@Composable
fun BoxScope.PlaylistNameAlert(
    visible: Boolean,
    title: String,
    name: String,
    backdrop: LayerBackdrop?,
    confirmLabel: String,
    onNameChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val strings = LocalStrings.current
    val focus = remember { FocusRequester() }

    // The caret goes to the end of what is already there, which a `String`-valued
    // field cannot express — it focuses at position zero, so the first letter
    // typed into a rename landed in *front* of the name. Measured.
    var field by remember(visible) {
        mutableStateOf(TextFieldValue(name, TextRange(name.length)))
    }
    // The caller stays the source of truth for the text; this only carries the
    // selection, which the caller has no opinion about.
    if (field.text != name && !visible) field = TextFieldValue(name, TextRange(name.length))

    // Keyed on `visible` so reopening asks again — a `FocusRequester` fired once
    // at composition would leave the second and every later opening without a
    // cursor, and the composable stays in the tree between them.
    LaunchedEffect(visible) {
        if (visible) runCatching { focus.requestFocus() }
    }

    GlassAlert(
        visible = visible,
        title = title,
        backdrop = backdrop,
        onDismiss = onDismiss,
        buttons = {
            GlassButton(strings.cancel, onDismiss)
            GlassButton(
                label = confirmLabel,
                onClick = onConfirm,
                primary = true,
                // An empty name is refused rather than accepted and trimmed to
                // nothing: a list with no name cannot be told from another on
                // the playlists page, which is the server's rule too.
                enabled = name.isNotBlank(),
            )
        },
    ) {
        GlassTextField(
            value = field,
            onValueChange = {
                field = it
                onNameChanged(it.text)
            },
            placeholder = strings.newPlaylist,
            modifier = Modifier.fillMaxWidth(),
            focusRequester = focus,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            // Return confirms, which is what the key says it does. Without this
            // it dismisses the keyboard and leaves the alert standing, and the
            // button it just uncovered is the one that was always going to be
            // pressed next.
            keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onConfirm() }),
        )
    }
}

/**
 * One confirmation, for something that cannot be undone.
 *
 * No text field, so no keyboard — but the same pane, the same scrim and the same
 * two-button row, because a delete that looked different from a rename would
 * read as a different app's dialog.
 */
@Composable
fun BoxScope.ConfirmAlert(
    visible: Boolean,
    title: String,
    detail: String,
    confirmLabel: String,
    backdrop: LayerBackdrop?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val strings = LocalStrings.current

    GlassAlert(
        visible = visible,
        title = title,
        backdrop = backdrop,
        onDismiss = onDismiss,
        buttons = {
            GlassButton(strings.cancel, onDismiss)
            GlassButton(confirmLabel, onConfirm, primary = true)
        },
    ) {
        Text(detail, color = Tokens.text2, fontSize = 14.sp)
    }
}
