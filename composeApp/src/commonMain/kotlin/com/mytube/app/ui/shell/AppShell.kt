package com.mytube.app.ui.shell

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.mytube.app.ui.home.Size
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.shell.rememberSelectionTick
import com.mytube.app.ui.theme.Tokens
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The frame every screen sits in: a bar above, a bar below, content between.
 *
 * ## Why the tabs are these four
 *
 * The server charter fixes four — *"Tab bar: Home · Subscriptions · History ·
 * Settings"* — and gives the rule that decides which of them survive here:
 * *"what earns a place there is what you move between while browsing."*
 *
 * History does not. It is a list this device keeps, opened when somebody wants
 * to find one video again, and it now sits beside Saved in Settings for that
 * reason. Three is also what the bar has room for: the search capsule beside
 * them takes a tab's width, and a fourth tab in what was left truncated every
 * label.
 */
enum class Tab { Home, Playlists, Settings }

@Composable
fun AppShell(
    current: Tab,
    onSelect: (Tab) -> Unit,
    /** Opens the search screen, from the button beside the tabs. */
    onSearch: () -> Unit = {},
    /**
     * How far the two bars have slid out of the way: 0 down, 1 gone.
     *
     * They leave while the reader is moving *down* a feed, which is the
     * behaviour every phone app with floating chrome has and this one did not:
     * two bars permanently occupying 136dp of a 2532px phone, on a screen whose
     * entire purpose is a column of pictures. They come back the moment a scroll
     * turns upward, so nothing is ever more than a flick away.
     *
     * **Animated by the caller, not here.** It was a boolean and the spring
     * lived inside this composable, which meant the miniplayer — a sibling of
     * the shell, not a child — had no way to read the same number, so it stayed
     * put while the tab bar it sits on slid away and ended up floating over the
     * feed. One value, animated once, used by everything that has to move with
     * the bars.
     */
    barsHidden: Float = 0f,
    /**
     * How far the bar has narrowed to make room for the miniplayer: 0 whole,
     * 1 collapsed.
     *
     * Separate from [barsHidden] because they are two answers to one question
     * and only one can be right at a time — see `BottomBar`. Animated by the
     * caller for [barsHidden]'s reason: the miniplayer is a sibling of this
     * shell and has to move on the same number, and a spring living in here
     * would be a number it cannot read.
     */
    collapse: Float = 0f,
    /**
     * What the top bar holds, under the status inset. Empty on most tabs.
     *
     * The feed's chip row goes here, and since the bar's other three occupants
     * left it is the only thing in it. A slot rather than the shell knowing
     * about chips: the shell has no idea what a topic is, and the day another
     * tab wants something pinned up there it does not have to be taught.
     *
     * A tab that passes nothing gets a bar the height of the status inset, and
     * `LocalTopBarHeight` reports that, so its list starts at the top of the
     * page rather than 56dp down it.
     */
    underTopBar: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val strings = LocalStrings.current
    val hide = barsHidden

    // The provider wraps the Box rather than sitting inside it. `Modifier.align`
    // is `BoxScope`, and CompositionLocalProvider's content lambda is not one —
    // put the provider inside and the two bars lose the alignment that puts them
    // at the top and the bottom of the screen.
    // What everything above blurs. Owned by the app rather than by this
    // composable, because the miniplayer is a *sibling* of the shell — it sits
    // over whichever screen is showing, including screens that are not this one
    // — and a state created here would be invisible to it.
    val backdrop = LocalBackdrop.current
    val source = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier

    // Measured by the bar and read by every list under it. It is no longer a
    // constant: the bar is the status inset plus a slot, and on a tab that fills
    // the slot with nothing there is no 56dp row left to reserve for.
    var topBarPx by remember { mutableStateOf(0f) }
    val topBarHeight = with(LocalDensity.current) { topBarPx.toDp() }

    CompositionLocalProvider(
        LocalBarsHidden provides hide,
        LocalTopBarHeight provides topBarHeight,
    ) {
        Box(Modifier.fillMaxSize().background(Tokens.bg)) {
            // The recording, and the flag that stops a tab opening a second
            // one inside it — see [glassSource], which crashed here.
            Box(Modifier.fillMaxSize().then(source)) {
                CompositionLocalProvider(LocalGlassRecording provides (backdrop != null)) {
                    content()
                }
            }

            // The top bar floats over the scrolling content rather than pushing
            // it down. That is the web app's arrangement and it is written down
            // there as a lesson: the bar is `absolute` and *"anything in the
            // shell's flow before <main> displaces every page"*, which cost that
            // app a 44px gap under the search field on every screen.
            TopBar(
                hidden = hide,
                onHeight = { topBarPx = it },
                underTopBar = underTopBar,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            // The pull-to-refresh pane, drawn here and not in the list that is
            // being pulled. See [PullGlass]: inside the recorded layer a sampled
            // backdrop crashes Skia rather than merely looking wrong.
            GlassRefreshIndicator(
                fraction = PullGlass.fraction,
                refreshing = PullGlass.refreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset {
                        IntOffset(
                            0,
                            (topBarHeight + PullGlass.extraTop + Space.sm).roundToPx(),
                        )
                    },
            )

            BottomBar(
                current = current,
                onSelect = onSelect,
                strings = { tab ->
                    when (tab) {
                        Tab.Home -> strings.navHome
                        Tab.Playlists -> strings.playlists
                        Tab.Settings -> strings.navSettings
                    }
                },
                onSearch = onSearch,
                searchLabel = strings.search,
                hidden = hide,
                collapse = collapse,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * Swallow every touch that lands on a bar and misses its buttons.
 *
 * Both bars float *over* the content, and a Compose modifier that only paints
 * does not take input — so a tap on the gap between two tab icons, or on the
 * empty half of the top bar, went straight through to whatever video card
 * happened to be underneath and opened it. On the bottom bar that is a video
 * opening every time somebody misses a tab by a few pixels.
 *
 * `indication = null`: this is not a button and must not flash like one. It is
 * the floor of the bar.
 */
@Composable
private fun Modifier.consumeTaps(): Modifier = clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = {},
)

@Composable
private fun TopBar(
    /** 0 when down, 1 when slid out of the way. */
    hidden: Float,
    onHeight: (Float) -> Unit,
    underTopBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Measured rather than computed, and now that is the only option: the bar is
    // the status inset plus whatever `underTopBar` turns out to be, and that is
    // a slot the shell cannot know the size of. A tab with nothing pinned gets a
    // bar the height of the status inset alone, which is what makes its list
    // start at the top of the page instead of 56dp down it.
    var height by remember { mutableStateOf(0f) }

    Box(
        modifier
            .fillMaxWidth()
            .onSizeChanged {
                height = it.height.toFloat()
                onHeight(height)
            }
            // `offset`, not `graphicsLayer { translationY }`.
            //
            // The glass samples from the node's **layout** position and knows
            // nothing about a draw-time transform, so a translated surface drew
            // its material at the place it would have been — which on a moving
            // bar meant no background at all, and the tab bar showing straight
            // through it. `offset` moves the node at placement, so the position
            // the effect reads is the position it is drawn at.
            .offset { IntOffset(0, (-hidden * height).roundToInt()) },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
        // windowInsetsTopHeight, not windowInsetsPadding. Padding on a Spacer
        // with no size of its own collapses to nothing, which is why the first
        // attempt left the clock sitting on the app's own mark.
        Spacer(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars),
        )
        // And this is now the whole bar.
        //
        // It used to be a capsule holding the logo, a search box shaped like a
        // field, and the household's avatar, with the chips as a second row
        // under it. None of the three belonged: search is a place you go, which
        // is why it is a button on the *bottom* bar now; the avatar answers a
        // question asked once a month, which is why it is a row in Settings; and
        // the logo did nothing at all. What was left was a band of glass the
        // width of the screen carrying one button, above the one row on it
        // anybody touches.
        //
        // So the chips move up into that place, and there is no pane behind
        // them. Each chip is already its own glass pill, so the row reads as a
        // set of floating controls — which is what it is. The movement is still
        // shared with the bottom bar, because the offset is on the Box around
        // this.
        underTopBar()
        }
    }
}

@Composable
private fun BottomBar(
    current: Tab,
    onSelect: (Tab) -> Unit,
    strings: (Tab) -> String,
    onSearch: () -> Unit,
    searchLabel: String,
    hidden: Float,
    /**
     * 0 while the bar is whole, 1 once it has narrowed to the selected tab.
     *
     * Collapsing is a *shape*, not a disappearance. With nothing playing the bar
     * slides off the bottom on scroll, which is what [hidden] does and what this
     * app has always done; with the miniplayer up it cannot, because sliding
     * would take away the one thing on screen saying something is still playing.
     *
     * So the capsule narrows to a circle holding the selected glyph alone, the
     * other two tabs shrink to nothing, and the space that opens between it and
     * the search button is exactly where the miniplayer lands — the caller walks
     * the player into it on this same fraction, so the three pieces arrive
     * together rather than one after another. Apple Music does this, and a
     * screen recording of that app is the reference.
     *
     * Everything below is a lerp on this one number, and nothing appears or
     * disappears while it runs. That is what keeps the picture bound: the
     * miniplayer is the same node at both ends, and a node removed and added
     * back is a player that rebinds and goes black.
     */
    collapse: Float,
    modifier: Modifier = Modifier,
) {
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val height = with(LocalDensity.current) { (navBar + Size.topBar).toPx() }
    // One generator for the whole bar. Four controls that all mean "go
    // somewhere" should feel identical, and four `remember`s would be four
    // Taptic generators warming up for the same row.
    val tick = rememberSelectionTick()

    Box(
        modifier
            .fillMaxWidth()
            .offset { IntOffset(0, (hidden * height).roundToInt()) }
            // The margins are outside the pane and the home indicator's inset is
            // one of them: a floating capsule keeps its shape and moves, where
            // the full-width band it replaces stretched down to the screen's
            // edge.
            .padding(
                start = GLASS_MARGIN,
                end = GLASS_MARGIN,
                bottom = navBar,
            ),
    ) {
        // Two panes with page between them, not one bar with a button in it.
        //
        // Search is not a tab: it is a place you go and come back from, and it
        // holds no scroll position of its own. Apple Music draws exactly this —
        // the tabs in one capsule, the magnifier in a circle beside it — and the
        // separation is what says the two are different kinds of thing.
        BoxWithConstraints(Modifier.fillMaxWidth().height(Size.topBar)) {
        // The room the three pieces share, inside the margins set above.
        val total = maxWidth
        val circle = Size.topBar
        val gap = Space.sm
        // Whole, the capsule takes everything the search button leaves; narrowed,
        // it is a circle. One item's share is a third of the whole, which is what
        // lets the selected glyph stay where it was while the others leave.
        val tabsWhole = total - circle - gap
        val tabsWidth = lerp(tabsWhole, circle, collapse)
        val itemWhole = tabsWhole / 3

        Row(
            Modifier.fillMaxWidth().height(Size.topBar),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(tabsWidth)
                    .height(Size.topBar)
                    .clip(GLASS_SHAPE)
                    // On each pane, not on the row. The gap between the two
                    // capsules is page, and a tap there belongs to the page.
                    .consumeTaps(),
            ) {
                BarBackdrop(Modifier.matchParentSize(), fromTop = false, shape = GLASS_SHAPE)

                // The lit pill, under the selected tab and travelling between
                // them.
                //
                // Animated by **index**, not by press: what moves is the mark,
                // and where it goes is a layout question this row already
                // answers — item `i` starts at `i * itemWhole`. Interpolating
                // the index therefore interpolates the position, and nothing
                // has to measure anything.
                //
                // At rest it is one item wide, inset a little; collapsed it is
                // the circle exactly, because by then it is the only thing left
                // with any width and the capsule has closed around it.
                // Two springs on one index, and the pill is the gap between
                // them.
                //
                // A single value would slide a rigid pane across. What the
                // reference does is *stretch*: the leading edge leaves before
                // the trailing one catches up, so the pill elongates across the
                // gap and settles at the far end. Two stiffnesses give exactly
                // that, and taking `min` and `max` of the pair means neither
                // edge has to know which way it is going — left to right or
                // right to left, the faster one is always the leading edge.
                //
                // At rest the two agree and the pill is one item wide again.
                val target = Tab.entries.indexOf(current).toFloat()
                val lead by animateFloatAsState(
                    targetValue = target,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = 900f,
                    ),
                    label = "lit-lead",
                )
                val trail by animateFloatAsState(
                    targetValue = target,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = 380f,
                    ),
                    label = "lit-trail",
                )
                val litFrom = min(lead, trail)
                val litTo = max(lead, trail) + 1f
                val litInset = Space.xs * (1f - collapse)
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = itemWhole * litFrom * (1f - collapse) + litInset)
                        .width(
                            (
                                lerp(itemWhole * (litTo - litFrom), circle, collapse) -
                                    litInset * 2
                                ).coerceAtLeast(0.dp),
                        )
                        .height(Size.topBar - Space.xs * 2)
                        // Gone by the time the bar has closed.
                        //
                        // Collapsed there is only one tab left, so a mark saying
                        // which one is a mark with nothing to distinguish — and
                        // drawn inside the circle the capsule has become, it
                        // read as a second ring nested in the first. The circle
                        // *is* the indicator by then.
                        .alpha(1f - collapse)
                        // White rather than a second surface token: `surface`
                        // and `surfaceHover` are six units apart, which is what
                        // the design system uses for a pointer hovering and what
                        // the Like button proved is invisible as a state.
                        .background(Tokens.text.copy(alpha = 0.12f), GLASS_SHAPE),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Size.topBar),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Tab.entries.forEachIndexed { index, tab ->
                        val selected = tab == current
                        TabItem(
                            icon = tabIcon(tab),
                            label = strings(tab),
                            // How much of this item the pill is standing on.
                            //
                            // The ink follows the pane rather than the state, so
                            // while it travels both ends read as chosen and the
                            // colour crosses with it — which is what the
                            // reference does and what makes the movement read as
                            // one thing moving rather than two lights swapping.
                            lit = (
                                min(litTo, index + 1f) - max(litFrom, index.toFloat())
                                ).coerceIn(0f, 1f),
                            collapse = collapse,
                            // The selected tab keeps a circle's worth of room and
                            // the others give theirs up. The three widths sum to
                            // the capsule's at both ends, so nothing is squashed
                            // on the way between them.
                            width = lerp(itemWhole, if (selected) circle else 0.dp, collapse),
                            onClick = {
                                // Pressing the tab already on scrolls it to the
                                // top rather than moving anywhere, and a tick
                                // for that would say the selection changed when
                                // it did not. Same rule as the chip row.
                                if (tab != current) tick()
                                onSelect(tab)
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.width(gap))

            // The miniplayer's berth, and deliberately empty.
            //
            // The player is a sibling of this shell — it floats over screens
            // with no tab bar at all — so what this row contributes is the
            // space, and the caller walks the player into it on the same
            // fraction.
            //
            // **A weight, not a width.** It was arithmetic once — the room left
            // after the two panes and the gaps — and the count of gaps changes
            // with the collapse, so the sum came out one gap short: the player
            // landed touching the search button with 8dp of nothing beyond it.
            // A weight cannot be off by a term.
            Spacer(Modifier.weight(1f))
            // The second gap, which only exists once there is something between
            // the two panes to have gaps either side of.
            Spacer(Modifier.width(gap * collapse))

            // Square, so `GLASS_SHAPE` at 50 percent draws a circle. No label:
            // the tabs carry one because they name a place among three, and a
            // magnifier alone has never needed telling apart from anything.
            val searchSource = remember { MutableInteractionSource() }
            val searchPress = rememberGlassPress(searchSource)
            Box(
                Modifier
                    .size(Size.topBar)
                    .pressSquish(searchPress)
                    .clip(GLASS_SHAPE)
                    .consumeTaps()
                    .clickable(
                        interactionSource = searchSource,
                        indication = null,
                        onClick = { tick(); onSearch() },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                BarBackdrop(Modifier.matchParentSize(), fromTop = false, shape = GLASS_SHAPE)
                Icon(
                    imageVector = SearchIcon,
                    contentDescription = searchLabel,
                    tint = Tokens.text2,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        }
    }
}

@Composable
private fun TabItem(
    icon: ImageVector,
    label: String,
    /** 0 for a tab the pill is nowhere near, 1 for the one it is standing on. */
    lit: Float,
    /** How far the bar has narrowed — see [BottomBar]'s `collapse`. */
    collapse: Float,
    /** The share of the capsule this item has at this point in the movement. */
    width: Dp,
    onClick: () -> Unit,
) {
    // The squash, on a tab as on every other control.
    //
    // No material of its own: a tab is a glyph and a word *on* the bar's pane,
    // not a second pane sitting on it — lighting a surface behind it would be
    // the selected-state fault in a new place, and selection here is already
    // said by the ink. What is left is the movement, which is the part that
    // answers the finger.
    val source = remember { MutableInteractionSource() }
    val press = rememberGlassPress(source)
    Column(
        modifier = Modifier
            // Measured, not intrinsic. The item's width is the caller's share of
            // a capsule that is itself moving, and an item that sized itself
            // would fight it — the glyph would drift as the pane closed rather
            // than sit still while the pane arrives around it.
            .width(width)
            // Nothing to press once it has no width. A tab that has given up its
            // room and still answers is the dead button §5 of the server charter
            // refuses, in the one shape nothing on screen distinguishes.
            .then(if (width > 0.dp) Modifier.pressSquish(press) else Modifier)
            .then(
                if (width > 0.dp) {
                    Modifier.clickable(
                        interactionSource = source,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            // Clipped, so the glyph goes with the room rather than spilling out
            // of a pane that has closed around it.
            .clipToBounds()
            .padding(vertical = Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            // The chosen tab wears the brand's colour.
            //
            // This app's rule was the opposite — *"the selected tab is
            // brighter, not a different colour: the design system has one accent
            // and it belongs to the brand, not to navigation"* — and it is
            // reversed deliberately, against a screen recording of Apple Music
            // where the accent is exactly what says which tab you are on. The
            // cost is that red now means two things in this app: what is live,
            // and where you are.
            //
            // Lerped rather than switched, so it crosses with the pill.
            tint = lerpColor(Tokens.text2, Tokens.brand, lit),
            modifier = Modifier.size(24.dp),
        )
        // The word leaves before the room does, and it takes its height with it.
        //
        // Fading alone was not enough: an invisible label still occupies its
        // line, so the column stayed 37dp of icon-plus-word inside a 56dp
        // circle and the glyph sat about 6dp above the middle of it. Measured,
        // and reported as the icon not being centred.
        //
        // Squared for the fade, because a label dimming in step with the width
        // is still legible at half a letter wide — which reads as text being
        // cut off rather than as a bar closing.
        Spacer(Modifier.height(2.dp * (1f - collapse)))
        Box(
            Modifier
                .height(LABEL_HEIGHT * (1f - collapse))
                .clipToBounds(),
        ) {
        Text(
            modifier = Modifier.alpha((1f - collapse) * (1f - collapse)),
            text = label,
            color = lerpColor(Tokens.text2, Tokens.brand, lit),
            fontSize = 10.sp,
            // Tight, and that is what fixes the gap.
            //
            // A `Text` with no line height carries the font's own leading — for
            // 10sp that is about 14sp, with more of the slack under the glyphs
            // than over them. The column is centred in the bar, so the *box* was
            // centred while the ink sat high in it, and the bar looked as though
            // it had less padding above than below. Reported exactly that way.
            lineHeight = 10.sp,
            fontWeight = if (lit > 0.5f) FontWeight.Medium else FontWeight.Normal,
        )
        }
    }
}

/**
 * The line the label occupies, so the column can give it back.
 *
 * Measured rather than asked for: a 10sp face with `lineHeight = 10.sp` lays
 * out in 10sp of box, and this is that in dp at the one density where dp and sp
 * agree — which is the only honest way to write it down without a `TextLayout`
 * pass nobody needs.
 */
private val LABEL_HEIGHT = 10.dp
