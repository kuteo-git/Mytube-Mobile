package com.mytube.app.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytube.app.ui.home.Size
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.theme.Tokens

/**
 * The padding every scrolling tab needs, in one place.
 *
 * Both bars float over the content — that is what makes them read as glass
 * rather than walls — so every list has to start below one and end above the
 * other. Insets alone are not enough: those describe the system's bars, not this
 * app's, and the top bar is 56dp *plus* the status bar.
 *
 * This exists because the same arithmetic was about to be written a fourth time.
 * The server charter records the web app learning the same thing the hard way:
 * *"this is the fourth thing to learn that the top bar's height belongs in
 * exactly one place."*
 */
@Composable
fun tabContentPadding(): PaddingValues = PaddingValues(
    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + Size.topBar,
    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + Size.topBar,
)

/** A heading over a tab's list, at the size the design system gives a section. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Tokens.text,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(horizontal = Space.lg, vertical = Space.md),
    )
}

/**
 * What a tab draws when it has nothing to show.
 *
 * Two lines, not one. A bare "nothing here" reads as a fault; the second line
 * says what would put something here, which is the difference between a screen
 * that looks broken and one that is simply waiting.
 */
@Composable
fun EmptyState(title: String, detail: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = Tokens.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.sm))
        Text(
            text = detail,
            color = Tokens.text2,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The three states every tab shares before it has content: loading, no server,
 * and a failure it can retry.
 *
 * Passed as lambdas rather than modelled as a common sealed type, because each
 * tab's state also carries its own content and forcing them into one hierarchy
 * would mean every tab knowing about every other tab's shape.
 */
@Composable
fun TabScaffold(
    loading: Boolean,
    needsServer: Boolean,
    failure: String,
    noServerTitle: String,
    setTheAddress: String,
    couldNotReach: String,
    tryAgain: String,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(color = Tokens.bg, modifier = Modifier.fillMaxSize()) {
        when {
            loading -> Centered { CircularProgressIndicator() }

            needsServer -> Centered {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(noServerTitle, color = Tokens.text)
                    Spacer(Modifier.height(Space.sm))
                    Button(onClick = onOpenSettings) { Text(setTheAddress) }
                }
            }

            failure.isNotEmpty() -> Centered {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(couldNotReach, color = Tokens.text)
                    Spacer(Modifier.height(Space.xs))
                    Text(failure, color = Tokens.text2, fontSize = 12.sp)
                    Spacer(Modifier.height(Space.md))
                    Button(onClick = onRetry) { Text(tryAgain) }
                }
            }

            else -> content()
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
