package com.mytube.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytube.app.ui.home.Size
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.i18n.LocalStrings
import androidx.compose.ui.draw.clip
import com.mytube.app.ui.theme.Tokens
import kotlin.math.roundToInt
import com.kyant.backdrop.backdrops.layerBackdrop

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
    modifier: Modifier = Modifier,
) {
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val height = with(LocalDensity.current) { (navBar + Size.topBar).toPx() }

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
        Row(
            Modifier.fillMaxWidth().height(Size.topBar),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(Size.topBar)
                    .clip(GLASS_SHAPE)
                    // On each pane, not on the row. The gap between the two
                    // capsules is page, and a tap there belongs to the page.
                    .consumeTaps(),
            ) {
                BarBackdrop(Modifier.matchParentSize(), fromTop = false, shape = GLASS_SHAPE)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Size.topBar)
                        .padding(horizontal = Space.sm),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Tab.entries.forEach { tab ->
                        TabItem(
                            icon = tabIcon(tab),
                            label = strings(tab),
                            selected = tab == current,
                            onClick = { onSelect(tab) },
                        )
                    }
                }
            }

            Spacer(Modifier.width(Space.sm))

            // Square, so `GLASS_SHAPE` at 50 percent draws a circle. No label:
            // the tabs carry one because they name a place among three, and a
            // magnifier alone has never needed telling apart from anything.
            Box(
                Modifier
                    .size(Size.topBar)
                    .clip(GLASS_SHAPE)
                    .consumeTaps()
                    .clickable(onClick = onSearch),
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

@Composable
private fun TabItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            // The selected tab is brighter, not a different colour: the design
            // system has one accent and it belongs to the brand, not to
            // navigation.
            tint = if (selected) Tokens.text else Tokens.text2,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = if (selected) Tokens.text else Tokens.text2,
            fontSize = 10.sp,
            // Tight, and that is what fixes the gap.
            //
            // A `Text` with no line height carries the font's own leading — for
            // 10sp that is about 14sp, with more of the slack under the glyphs
            // than over them. The column is centred in the bar, so the *box* was
            // centred while the ink sat high in it, and the bar looked as though
            // it had less padding above than below. Reported exactly that way.
            lineHeight = 10.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}
