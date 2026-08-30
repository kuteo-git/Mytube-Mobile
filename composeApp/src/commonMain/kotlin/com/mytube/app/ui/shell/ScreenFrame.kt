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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
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
 *
 * The miniplayer is read from a composition local rather than passed in. It is
 * an ambient fact about the whole shell — the bar is there or it is not — and
 * threading a boolean through seven screen signatures to say so is how one of
 * them gets forgotten and its last row stays hidden underneath it.
 */
@Composable
fun tabContentPadding(): PaddingValues = tabContentPadding(LocalMiniPlayerShowing.current)

/** Whether the miniplayer bar is on screen, for the padding above to read. */
val LocalMiniPlayerShowing = staticCompositionLocalOf { false }

/**
 * How far the floating bars have slid out of the way: 0 down, 1 gone.
 *
 * Read by anything else pinned to the top of a tab — today that is the feed's
 * chip row. It was pinned at a *fixed* offset below the top bar, so scrolling
 * down took both bars away and left the chips floating alone under a band of
 * empty black. The chips are chrome too; they leave with the chrome.
 *
 * A local rather than a parameter for the reason `LocalMiniPlayerShowing` is
 * one: it is an ambient fact about the shell, and threading a float through
 * every screen signature is how the next screen to pin something forgets.
 */
val LocalBarsHidden = compositionLocalOf { 0f }

@Composable
private fun tabContentPadding(miniPlayer: Boolean): PaddingValues = PaddingValues(
    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + Size.topBar,
    // The tab bar, and the miniplayer sitting on it when there is one.
    //
    // Without the second term the bar covers whatever is last in the list, and
    // the last row of a feed is the one nobody can scroll past to reach. It is
    // a padding rather than a margin on the bar for the reason every list here
    // uses one: the content scrolls *under* the bar and stops clear of it.
    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        Size.topBar + (if (miniPlayer) Size.miniPlayer else 0.dp),
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
    // `offset`, not `padding`.
    //
    // Padding puts the indicator inside a smaller box, and the indicator draws
    // itself *above* its own origin while the finger is pulling — so the top of
    // the disc was clipped by the padding box for the whole of the gesture, and
    // what a reader saw was a spinner with its head cut off. An offset moves
    // where it draws without changing what may be drawn.
    //
    // The distance is the same arithmetic as [tabContentPadding], and for the
    // same reason: the bar is 56dp *plus* the status bar, and it floats over the
    // list. A little more, so the disc clears the bar rather than touching it.
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
        Size.topBar + Space.sm
    val offsetPx = with(LocalDensity.current) { top.roundToPx() }

    PullToRefreshDefaults.Indicator(
        state = state,
        isRefreshing = isRefreshing,
        containerColor = Tokens.surface,
        color = Tokens.text,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset { IntOffset(0, offsetPx) },
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
fun skeletonShade(): Color {
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
    return Tokens.surface.copy(alpha = alpha)
}

/**
 * The watch screen while it is loading.
 *
 * Every other screen showed a pulsing outline of what was coming and this one
 * showed a spinner, which says "something is happening" where the others say
 * "this is what is arriving". A spinner over a black rectangle is also the same
 * picture as a video that failed, and telling those apart is the whole reason
 * this app draws skeletons at all.
 */
@Composable
fun WatchSkeleton(modifier: Modifier = Modifier) {
    val shade = skeletonShade()
    Column(modifier.fillMaxWidth()) {
        // The picture keeps its 16:9 box in every state, so nothing below it
        // moves when the video arrives.
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(shade))
        Column(Modifier.padding(Space.lg)) {
            Box(Modifier.fillMaxWidth(0.9f).height(20.dp).clip(RoundedCornerShape(4.dp)).background(shade))
            Spacer(Modifier.height(Space.sm))
            Box(Modifier.fillMaxWidth(0.6f).height(20.dp).clip(RoundedCornerShape(4.dp)).background(shade))
            Spacer(Modifier.height(Space.lg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(Size.avatar).clip(CircleShape).background(shade))
                Spacer(Modifier.width(Space.md))
                Box(Modifier.fillMaxWidth(0.45f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(shade))
            }
        }
    }
}

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


/**
 * Whether the floating bars should be on screen, given how a list is moving.
 *
 * Reading direction rather than position: hiding below a fixed scroll depth is
 * the rule that makes the bars vanish while somebody is reading half way down a
 * feed and stay gone while they scroll *back*, which is exactly when the tabs are
 * wanted. `lastScrolledForward` is the list's own answer to "which way did the
 * last movement go", so the bars leave on the way down and return on the way up.
 *
 * Always shown at the very top. A feed that opens with no top bar looks like a
 * screen that failed to draw its chrome.
 *
 * `derivedStateOf` because these three read from the scroll on every frame of
 * every fling, and without it the whole shell recomposes at 60fps to answer a
 * boolean that changes twice a minute.
 */
@Composable
fun rememberBarsVisible(listState: LazyListState): Boolean {
    val visible by remember(listState) {
        derivedStateOf {
            !listState.canScrollBackward || !listState.lastScrolledForward
        }
    }
    return visible
}
