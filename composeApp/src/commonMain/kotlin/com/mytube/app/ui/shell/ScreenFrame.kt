package com.mytube.app.ui.shell

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
            // The skeleton, not a spinner. A spinner in the middle of an empty
            // screen says "wait"; this says what is coming, and the page then
            // fills in rather than appearing.
            loading -> Column(
                Modifier.fillMaxSize().padding(top = tabContentPadding()
                    .calculateTopPadding()),
            ) { FeedSkeleton() }

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

/**
 * The pull-to-refresh spinner, clear of the floating top bar.
 *
 * It exists because the three tabs passed `indicator = {}` — which does not move
 * the indicator out of the way, it **removes it**. The gesture worked and
 * refreshed the list, and nothing on screen ever said so: a pull that produces
 * no spinner reads as a pull that did nothing, so people pull again.
 *
 * The offset is the same arithmetic as [tabContentPadding] and for the same
 * reason: the bar is 56dp *plus* the status bar, and it floats over the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.TabRefreshIndicator(state: PullToRefreshState, isRefreshing: Boolean) {
    PullToRefreshDefaults.Indicator(
        state = state,
        isRefreshing = isRefreshing,
        containerColor = Tokens.surface,
        color = Tokens.text,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
                    Size.topBar,
            ),
    )
}

/**
 * The shape of a feed, before the feed arrives.
 *
 * A spinner in the middle of an empty screen says "wait" and nothing else. This
 * says *what* is coming: the thumbnail's box, the avatar's circle, two lines of
 * title. The page then fills in rather than appearing, which is the difference
 * between a screen that felt slow and one that felt broken.
 *
 * It pulses rather than sweeping a gradient across. A sweep is prettier and it
 * is a second animation to keep at 60fps on a television; a fade between two
 * greys says the same thing and costs one alpha.
 */
@Composable
fun FeedSkeleton(modifier: Modifier = Modifier, cards: Int = 3) {
    val pulse = rememberInfiniteTransition(label = "skeleton")
    val alpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton-alpha",
    )
    val shade = Tokens.surface.copy(alpha = alpha)

    Column(modifier.fillMaxWidth()) {
        repeat(cards) {
            Column(Modifier.padding(bottom = Space.xxl)) {
                Box(
                    Modifier
                        .padding(horizontal = Space.md)
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(shade),
                )
                Spacer(Modifier.height(Space.md))
                Row(Modifier.padding(horizontal = Space.lg)) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(shade))
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(shade),
                        )
                        Spacer(Modifier.height(Space.sm))
                        Box(
                            Modifier
                                // Short, like a channel name. Two full-width
                                // bars read as a paragraph, and the eye notices
                                // that the real card is not shaped like that.
                                .fillMaxWidth(0.45f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(shade),
                        )
                    }
                }
            }
        }
    }
}
