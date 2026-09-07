package com.mytube.app.ui.shell

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.mytube.app.ui.home.Radius
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

/**
 * The same, for a page opened from a menu rather than a tab.
 *
 * Saved, History and a channel have no tab bar and no chip row; what they have
 * at the top is [DetailBack]'s arrow, drawn over the content, and at the
 * bottom nothing but the miniplayer when there is one. They used to borrow
 * `tabContentPadding`, which was right about the top by coincidence — the arrow
 * row is exactly `Size.topBar` — and wrong about the bottom by a tab bar's
 * height.
 */
@Composable
fun detailContentPadding(): PaddingValues = PaddingValues(
    top = detailTopChrome(),
    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        (if (LocalMiniPlayerShowing.current) Size.miniPlayer else 0.dp) + Space.md,
)

/**
 * The way out of a page reached from a menu, drawn over whatever it shows.
 *
 * Every such page needs one and needs it over *every* state, or a page that will
 * not load is a dead end: Android's system back leaves the app, and iOS has no
 * system back at all. Five screens wrote this same 48dp box, and five copies of
 * a control are five controls that drift apart.
 */
@Composable
fun BoxScope.DetailBack(onBack: () -> Unit, label: String) {
    Box(
        Modifier
            .align(Alignment.TopStart)
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                start = Space.xs,
            )
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(DetailBackIcon, label, tint = Tokens.text, modifier = Modifier.size(24.dp))
    }
}

/** The status inset plus the row [DetailBack]'s arrow sits in. */
@Composable
private fun detailTopChrome(): Dp =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + Size.topBar

/**
 * Whatever chrome a screen has at the top, whichever kind of screen it is.
 *
 * Inside the shell that is the measured bar; outside it, the back arrow's row.
 * The skeleton needs it and cannot know which of the two it is being drawn in —
 * `TabScaffold` is used by tabs and by the two pages opened from Settings alike.
 */
@Composable
private fun topChrome(): Dp {
    val bar = LocalTopBarHeight.current
    return if (bar > 0.dp) bar else detailTopChrome()
}

/** Whether the miniplayer bar is on screen, for the padding above to read. */
val LocalMiniPlayerShowing = staticCompositionLocalOf { false }

/**
 * How tall the top bar turned out to be, measured by [AppShell].
 *
 * It used to be a constant — the status inset plus 56dp — and that was true
 * while every tab carried the same bar. It is not any more: the bar's logo,
 * search box and avatar have all left it, so what remains is the status inset
 * plus whatever a tab pins under it. On Home that is the chip row; on the other
 * two it is nothing at all, and reserving 56dp there left a band of empty page
 * above the first row of every list.
 */
val LocalTopBarHeight = compositionLocalOf { 0.dp }

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
    // Measured, not computed. See [LocalTopBarHeight]: the bar's height is now a
    // property of what the tab pinned in it, and only the bar can know that. The
    // status inset is inside the measurement, because the bar reserves it.
    top = LocalTopBarHeight.current,
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
    /**
     * What this tab's list looks like before it has one.
     *
     * A parameter rather than a constant, because `FeedSkeleton` stood in for
     * five tabs and only three of them are feeds: Subscriptions is a column of
     * 48dp circles and Playlists is a column of collection rows, and a stack of
     * 16:9 pictures promised a feed that never arrived on either.
     */
    skeleton: @Composable () -> Unit = { FeedSkeleton() },
    content: @Composable () -> Unit,
) {
    Surface(color = Tokens.bg, modifier = Modifier.fillMaxSize()) {
        when {
            // The skeleton, not a spinner. A spinner in the middle of an empty
            // screen says "wait"; this says what is coming, and the page then
            // fills in rather than appearing.
            loading -> Column(
                Modifier.fillMaxSize().padding(top = topChrome()),
            ) { skeleton() }

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
 * Where a pull-to-refresh gesture is up to, published for the shell to draw.
 *
 * # Why the indicator is not drawn where it is used
 *
 * It has to be **outside the layer the shell records**. A sampled backdrop that
 * is itself inside that recording asks Skia to filter an image that contains the
 * filter, and the answer is not a wrong picture — it is a crash. Measured on the
 * simulator, on launch, the first time this was given `liquidGlass` while it
 * still lived inside the list:
 *
 * ```
 * EXC_BAD_ACCESS  Could not determine thread index for stack guard region
 * SkRasterPipeline::run … SkBlurImageFilter::onGetOutputLayerBounds …
 * ```
 *
 * — a stack overflow inside the image-filter bounds walk. The charter's rule
 * about a control "sampling a recording of itself" was written as an appearance
 * problem; this is what it actually costs.
 *
 * So the screens say *where the pull has got to* and `AppShell` — which draws
 * the bars, and is a sibling of the recording rather than inside it — draws the
 * pane. The same arrangement, and the same reason, as the bars themselves.
 */
internal object PullGlass {
    var fraction by mutableFloatStateOf(0f)
    var refreshing by mutableStateOf(false)

    /** How far down the pane goes, over and above the bar it hangs under. */
    var extraTop by mutableStateOf(0.dp)
}

/**
 * Publish the pull, and draw nothing.
 *
 * `indicator = {}` on a `PullToRefreshBox` does not move an indicator, it
 * removes one — the fault this function was written to fix, recorded in the
 * charter. So it still occupies the slot, and what it puts there is a report
 * rather than a picture.
 */
@Composable
fun BoxScope.TabRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    /**
     * Anything above this list that the bar's own measurement does not cover.
     *
     * Nothing does today: the chip row used to need `Size.chipRow` here, because
     * it was pinned under a bar whose height the indicator assumed rather than
     * measured, and the pane came out behind the chips — a disc peeking out from
     * under "Gaming". The bar reports its real height now, chips included.
     */
    extraTop: Dp = 0.dp,
) {
    // Read **here**, in composition, and not inside the effect.
    //
    // A state read inside `SideEffect` subscribes nothing: the effect runs after
    // a recomposition, it does not cause one. So the pane was only ever updated
    // when something *else* recomposed this slot — which happens constantly
    // while a finger is dragging a list, and stops the instant it lifts. The
    // pane then kept the last fraction it had been handed and stayed on screen.
    // Reported as the loading not hiding.
    val distance = state.distanceFraction

    // One knock the moment the pull is far enough to count, while the finger is
    // still down — which is the only moment feedback can change what somebody
    // does next. Firing it when `isRefreshing` turns true would be a buzz
    // arriving after the decision, reporting rather than confirming.
    //
    // An impact rather than a selection tick: this is a threshold being met,
    // not a value passing through a position.
    val knock = rememberLandingKnock()
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(distance >= 1f) {
        if (distance >= 1f && !armed) {
            armed = true
            knock()
        } else if (distance < 1f) {
            // Re-armed only on the way back down, so a finger resting either
            // side of the line does not buzz on every frame.
            armed = false
        }
    }

    SideEffect {
        PullGlass.fraction = distance
        PullGlass.refreshing = isRefreshing
        PullGlass.extraTop = extraTop
    }
    DisposableEffect(Unit) {
        onDispose {
            PullGlass.fraction = 0f
            PullGlass.refreshing = false
        }
    }
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
 *
 * **It draws no picture.** This is only ever the page *under* the video, and
 * `WatchScreen` holds the 16:9 slot open in every state — the outer box that
 * stops the title sliding under the picture during a drag. Drawing one here as
 * well put two 16:9 boxes on top of each other: the player's own black slot,
 * and a grey placeholder for it underneath. Measured on the phone at 1170
 * wide, 658px of black and then 658px of grey, with the title bars pushed a
 * whole picture's height down the screen.
 */
@Composable
fun WatchSkeleton(modifier: Modifier = Modifier) {
    val shade = skeletonShade()

    // It scrolls because the real page does, and because a `Column` that runs
    // out of room does not clip its overflow — it measures what is left with a
    // maximum height of zero and then draws the children at their natural size
    // anyway, one on top of the other. Reported as a light band inside the last
    // rail thumbnail, which is exactly what two translucent copies of the same
    // shape look like: measured, 4px of overlap on a 914dp emulator and 80px on
    // an 844dp phone, and none at all once the density was dropped far enough
    // for the whole thing to fit.
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        // Title: 20sp over 28sp of line, two lines of it.
        Column(Modifier.padding(start = Space.lg, end = Space.lg, top = Space.md)) {
            SkeletonLine(shade, 1f, 20.dp)
            Spacer(Modifier.height(8.dp))
            SkeletonLine(shade, 0.7f, 20.dp)
        }

        // `ChannelRow`: a 40dp avatar, the name over the subscriber count, and
        // the Subscribe pill at the far end.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(shade))
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                SkeletonLine(shade, 0.5f, 15.dp)
                Spacer(Modifier.height(6.dp))
                SkeletonLine(shade, 0.32f, 12.dp)
            }
            Spacer(Modifier.width(Space.sm))
            Box(
                Modifier
                    .width(104.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(shade),
            )
        }

        // `WatchActions`: the like/dislike pill, then Share and Save. Their
        // widths are the row's real ones, because a row of three equal pills is
        // a different picture from the one that arrives.
        Spacer(Modifier.height(Space.md))
        Row(
            Modifier.padding(horizontal = Space.lg),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            SkeletonPill(shade, 116.dp)
            SkeletonPill(shade, 118.dp)
            SkeletonPill(shade, 104.dp)
        }

        // `DescriptionBox`: a 12dp pane holding the counts line and two lines
        // of the description itself.
        Spacer(Modifier.height(Space.md))
        Column(
            Modifier
                .padding(horizontal = Space.lg)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(shade)
                .padding(Space.md),
        ) {
            SkeletonLine(shade, 0.55f, 14.dp)
            Spacer(Modifier.height(Space.sm))
            SkeletonLine(shade, 1f, 14.dp)
            Spacer(Modifier.height(6.dp))
            SkeletonLine(shade, 0.8f, 14.dp)
        }

        // `CommentsHeading` — one 18sp line in its own pane.
        Spacer(Modifier.height(Space.lg))
        SkeletonPane(shade, Modifier.padding(horizontal = Space.lg)) {
            SkeletonLine(shade, 0.45f, 18.dp)
        }

        // `UpNextRail`'s header, which is two lines: what plays next, and whose
        // channel it is.
        Spacer(Modifier.height(Space.lg))
        SkeletonPane(shade, Modifier.padding(horizontal = Space.lg)) {
            SkeletonLine(shade, 0.85f, 14.dp)
            Spacer(Modifier.height(6.dp))
            SkeletonLine(shade, 0.4f, 12.dp)
        }

        // The rail's two chips, and its rows: a 168dp thumbnail with three
        // lines beside it, which is `SuggestionRow`'s own shape.
        Spacer(Modifier.height(Space.md))
        Row(
            Modifier.padding(horizontal = Space.lg),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            SkeletonPill(shade, 64.dp, 36.dp)
            SkeletonPill(shade, 132.dp, 36.dp)
        }
        Spacer(Modifier.height(Space.md))
        repeat(2) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.sm)) {
                Box(
                    Modifier
                        .width(168.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(shade),
                )
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    SkeletonLine(shade, 1f, 13.dp)
                    Spacer(Modifier.height(6.dp))
                    SkeletonLine(shade, 0.75f, 13.dp)
                    Spacer(Modifier.height(6.dp))
                    SkeletonLine(shade, 0.5f, 12.dp)
                }
            }
        }
    }
}

/** One line of stand-in text, at the height the real line occupies. */
@Composable
private fun SkeletonLine(shade: Color, width: Float, height: Dp) {
    Box(
        Modifier
            .fillMaxWidth(width)
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(shade),
    )
}

/** A capsule control: an action pill, a chip, the Subscribe button. */
@Composable
private fun SkeletonPill(shade: Color, width: Dp, height: Dp = 40.dp) {
    Box(
        Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(percent = 50))
            .background(shade),
    )
}

/**
 * One of the page's own panes, at `glassControl`'s 12dp radius.
 *
 * The pane is drawn in the same shade as what is inside it, so what reads is
 * its outline rather than two greys arguing — the real thing is a translucent
 * surface with text on it, and a skeleton that picks out the text more strongly
 * than the pane says the wrong thing about which is which.
 */
@Composable
private fun SkeletonPane(
    shade: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(shade)
            .padding(horizontal = Space.md, vertical = Space.sm),
        content = content,
    )
}

/**
 * A stand-in for a feed of video cards.
 *
 * **Every measurement here is `VideoCard`'s, and that is the whole point.** It
 * used to be its own idea of a card — inset by `Space.md` with a 12dp radius,
 * one title bar, and `Space.xxl` between cards — which is the *web* card's
 * shape. The app's card has run edge to edge with square corners since the feed
 * took YouTube's shape, so the page visibly changed geometry the instant it
 * loaded: the pictures grew sideways into both margins and lost their corners.
 * A skeleton that does that is worse than none, because it says the wrong thing
 * confidently.
 *
 * Keep the two in step. If `VideoCard`'s padding moves, this moves.
 */
@Composable
fun FeedSkeleton(modifier: Modifier = Modifier, cards: Int = 3) {
    val shade = skeletonShade()

    Column(modifier.fillMaxWidth()) {
        repeat(cards) {
            // Edge to edge and square, exactly as the card is.
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(shade),
            )
            // `VideoCard`'s meta row: `start = lg, end = sm, top = md,
            // bottom = md`, and the avatar is `Size.avatar`.
            Row(
                Modifier.padding(
                    start = Space.lg,
                    end = Space.sm,
                    top = Space.md,
                    bottom = Space.md,
                ),
            ) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(shade))
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    // Two bars, because the title takes two lines at 20sp and a
                    // single bar leaves the row a third too short — the cards
                    // below it then jump upward as the text arrives.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shade),
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            // Short, like the second line of a title that has
                            // run out of words. Two full-width bars read as a
                            // paragraph, which no card is.
                            .fillMaxWidth(0.7f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shade),
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            // One meta line: `channel · views · age`.
                            .fillMaxWidth(0.55f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shade),
                    )
                }
            }
            Spacer(Modifier.height(Space.md))
        }
    }
}

/**
 * A stand-in for a list of channels — Subscriptions.
 *
 * Its own shape rather than `FeedSkeleton`, because that row is a 48dp circle
 * beside two short lines and nothing else. Standing a column of 16:9 pictures
 * in for it promises a feed and delivers a list, which is the fault this file
 * has just been corrected for one screen down.
 */
@Composable
fun ChannelListSkeleton(modifier: Modifier = Modifier, rows: Int = 8) {
    val shade = skeletonShade()

    Column(modifier.fillMaxWidth()) {
        repeat(rows) {
            Row(
                Modifier.padding(horizontal = Space.lg, vertical = Space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(shade))
                Spacer(Modifier.width(Space.md))
                Column {
                    Box(
                        Modifier
                            .width(160.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shade),
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .width(96.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shade),
                    )
                }
            }
        }
    }
}

/**
 * A stand-in for a list of collections — the Playlists tab.
 *
 * A playlist row is a 16:9 thumbnail beside its title, with a strip of the
 * picture behind showing above it — the mark that says "a stack, not a video"
 * before the title is read. The strip is in the skeleton for that reason: it is
 * the one thing that distinguishes this list from a list of videos, and leaving
 * it out is what would make the two look alike while loading and different
 * afterwards.
 */
@Composable
fun CollectionListSkeleton(modifier: Modifier = Modifier, rows: Int = 6) {
    val shade = skeletonShade()

    Column(modifier.fillMaxWidth()) {
        repeat(rows) {
            Row(
                Modifier.padding(horizontal = Space.lg, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.width(160.dp)) {
                    Box(
                        Modifier
                            .padding(horizontal = 6.dp)
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(shade),
                    )
                    Spacer(Modifier.height(2.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(Radius.thumbnail))
                            .background(shade),
                    )
                }
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.8f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shade),
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.4f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shade),
                    )
                }
            }
        }
    }
}

/**
 * The chip row, while the topics it is built from are still being fetched.
 *
 * Home pins this row above the feed, so a loading state without it is a screen
 * whose whole content shifts down by a row the moment it arrives. Fixed widths
 * rather than words: the real chips are as wide as the topics the server names,
 * and inventing plausible ones here would be a translation nobody wrote.
 */
@Composable
fun ChipRowSkeleton(modifier: Modifier = Modifier) {
    val shade = skeletonShade()

    Row(
        modifier.padding(horizontal = Space.md),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        listOf(56.dp, 88.dp, 72.dp, 96.dp, 80.dp).forEach { width ->
            Box(
                Modifier
                    .width(width)
                    .height(Size.chip)
                    .clip(GlassRadius.control)
                    .background(shade),
            )
        }
    }
}


/**
 * Whether the floating bars should be on screen, given how a list is moving.
 *
 * Reading direction rather than position: hiding below a fixed scroll depth is
 * the rule that makes the bars vanish while somebody is reading half way down a
 * feed and stay gone while they scroll *back*, which is exactly when the tabs are
 * wanted.
 *
 * Always shown at the very top. A feed that opens with no top bar looks like a
 * screen that failed to draw its chrome.
 *
 * # Direction alone is not enough, and it was reported as jitter
 *
 * This used to be `listState.lastScrolledForward`, which is the list's own
 * answer to "which way did the last movement go" — and it answers that about
 * **the last pixel**. A finger resting on a moving list wobbles by a pixel or
 * two in both directions, so the bars came back and left again under a thumb
 * that was, as far as its owner was concerned, holding still.
 *
 * The fix is not a debounce or a throttle. Both of those are about *time*, and
 * nothing here is too fast — a bar that appeared 200ms after the wobble would be
 * the same fault, late. What is wrong is that a movement of any size counts as a
 * change of mind, so the answer is a **distance the movement has to cover before
 * it counts**: [DIRECTION_THRESHOLD] of travel in one direction flips the bars,
 * and travel the other way has to cover that distance again to flip them back.
 *
 * Distance accumulates and resets on a reversal rather than summing, so a slow
 * drift never adds up to a flip and a decisive flick always does.
 */
@Composable
fun rememberBarsVisible(listState: LazyListState): Boolean {
    val threshold = with(LocalDensity.current) { DIRECTION_THRESHOLD.toPx() }
    // At the top the bars are always up, whatever the last movement was. Kept as
    // `derivedStateOf` for the reason it always was: this is read on every frame
    // of every fling, and without it the whole shell recomposes at 60fps to
    // answer a boolean that changes twice a minute.
    val atTop by remember(listState) {
        derivedStateOf { !listState.canScrollBackward }
    }
    var hidden by remember(listState) { mutableStateOf(false) }

    LaunchedEffect(listState, threshold) {
        var previous = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        // How far the list has travelled in the current direction. Reset, not
        // added to, when the direction changes.
        var travelled = 0f

        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { position ->
            val (index, offset) = position
            val (wasIndex, wasOffset) = previous
            previous = position

            // Within one item the offset is a real distance. Across items it is
            // not — the next item has its own height and its own zero — so a
            // crossing counts as one decisive movement in that direction rather
            // than as a number this cannot know.
            val delta = when {
                index == wasIndex -> (offset - wasOffset).toFloat()
                index > wasIndex -> threshold
                else -> -threshold
            }
            if (delta == 0f) return@collect

            travelled = if (travelled == 0f || (travelled > 0f) == (delta > 0f)) {
                travelled + delta
            } else {
                delta
            }

            if (travelled >= threshold) hidden = true
            if (travelled <= -threshold) hidden = false
        }
    }

    return atTop || !hidden
}

/**
 * How far a list has to move one way before the bars believe it.
 *
 * 48dp — about a finger's width, and a third of a card's thumbnail. Small enough
 * that a deliberate short flick still hides the chrome, large enough that
 * nothing a resting thumb does reaches it.
 */
private val DIRECTION_THRESHOLD = 48.dp

/**
 * A screen opened *from* another one: a title, a way back, and content.
 *
 * # Why this exists
 *
 * Settings was one long page — an address, a shelf, two sliders, a text field
 * and a list of languages, all scrolling past each other under one title. What
 * belongs on a phone's settings screen is a **menu**: a row per thing, and the
 * thing itself on its own screen. That is what the platform's own Settings does,
 * and it is what makes a row's value legible at a glance without reading the
 * control that sets it.
 *
 * # The back arrow is drawn over the content, and it has to be
 *
 * These screens have no tab bar. Android's system back leaves the app and iOS
 * has no system back at all, so a detail that would not load is otherwise a dead
 * end — the same argument the channel page and the saved shelf each record, and
 * the third time is when it becomes a function.
 */
@Composable
fun DetailScaffold(
    title: String,
    onBack: () -> Unit,
    backLabel: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(color = Tokens.bg, modifier = Modifier.fillMaxSize()) {
        // Recorded, so the miniplayer floating over this page is glass here too.
        Box(Modifier.fillMaxSize().glassSource()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(detailContentPadding()),
            ) {
                // The heading is the first thing *in* the page, with the arrow
                // floating over it — not a row sharing a line with the arrow,
                // which is what this drew and what made two screens out of the
                // five in this menu look like a different app.
                //
                // Every other page reached from a menu does it this way: Saved,
                // History, a channel and the search results all put a
                // [ScreenTitle] at the head of their list under a [DetailBack].
                // The heading then scrolls away with what it heads, and the way
                // out stays where a thumb left it.
                ScreenTitle(title)
                content()
            }

            DetailBack(onBack, backLabel)
        }
    }
}

/** The same chevron the channel page and the saved shelf carry. */
private val DetailBackIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "DetailBack",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(15.5f, 4f)
            lineTo(7.5f, 12f)
            lineTo(15.5f, 20f)
            lineTo(17f, 18.5f)
            lineTo(10.5f, 12f)
            lineTo(17f, 5.5f)
            close()
        }
    }.build()
}
