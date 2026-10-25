package com.mytube.app.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.shell.GlassButton
import com.mytube.app.ui.theme.Tokens

/**
 * A whole page that is a sentence and, when there is something to do about it,
 * one button.
 *
 * The button takes focus as it arrives. On a phone a message with a button under
 * it is read and then tapped; on a television nothing is focused until something
 * asks to be, and a page whose only control is not focused is a page where every
 * key on the remote does nothing.
 */
@Composable
fun TvMessage(
    title: String,
    detail: String,
    actionLabel: String,
    /** Null for a message with nothing to press — the button is then absent. */
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    if (onAction != null) {
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                color = Tokens.text,
                fontSize = 20.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            if (detail.isNotEmpty()) {
                Spacer(Modifier.height(Space.sm))
                Text(
                    text = detail,
                    color = Tokens.text2,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                )
            }
            if (onAction != null) {
                Spacer(Modifier.height(Space.xl))
                GlassButton(
                    label = actionLabel,
                    onClick = onAction,
                    primary = true,
                    modifier = Modifier.focusRequester(focus),
                )
            }
        }
    }
}
