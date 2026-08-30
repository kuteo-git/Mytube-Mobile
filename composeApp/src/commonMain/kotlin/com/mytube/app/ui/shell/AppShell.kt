package com.mytube.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytube.app.ui.home.Size
import com.mytube.app.ui.home.avatarColourFor
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.theme.Tokens
import kotlin.math.roundToInt
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.Color

/**
 * The frame every screen sits in: a bar above, a bar below, content between.
 *
 * ## Why the tabs are these four
 *
 * The server charter fixes them: *"Tab bar: Home · Subscriptions · History ·
 * Settings"*, with Storage and Activity living inside Settings. It also gives
 * the reason a fifth is not added lightly — *"the bottom bar is full at five,
 * and what earns a place there is what you move between while browsing."*
 */
enum class Tab { Home, Subscriptions, History, Settings }

@Composable
fun AppShell(
    current: Tab,
    onSelect: (Tab) -> Unit,
    /** The signed-in member's initial, or empty before anybody has chosen. */
    profileInitial: String = "",
    onSearch: () -> Unit = {},
    /** Opens the household's profile picker from the avatar. */
    onOpenProfiles: () -> Unit = {},
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
     * Drawn directly under the search row, inside the same bar.
     *
     * The feed's chip row goes here. It used to be pinned by the Home screen at
     * a fixed offset below the bar, and being a *separate* floating surface is
     * what made it wrong twice over: it stayed put when the bars slid away on
     * scroll, and it carried its own backdrop, so a frosted bar sat directly on
     * an opaque strip that happened to be the same colour. One bar, one
     * material, one movement.
     *
     * A slot rather than the shell knowing about chips: the shell has no idea
     * what a topic is, and the day another tab wants something pinned under the
     * bar it does not have to be taught.
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
    val haze = LocalHaze.current
    val source = if (haze != null) Modifier.hazeSource(haze) else Modifier

    CompositionLocalProvider(LocalBarsHidden provides hide) {
        Box(Modifier.fillMaxSize().background(Tokens.bg)) {
            Box(Modifier.fillMaxSize().then(source)) { content() }

            // The top bar floats over the scrolling content rather than pushing
            // it down. That is the web app's arrangement and it is written down
            // there as a lesson: the bar is `absolute` and *"anything in the
            // shell's flow before <main> displaces every page"*, which cost that
            // app a 44px gap under the search field on every screen.
            TopBar(
                profileInitial = profileInitial,
                onSearch = onSearch,
                onOpenProfiles = onOpenProfiles,
                hidden = hide,
                underTopBar = underTopBar,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            BottomBar(
                current = current,
                onSelect = onSelect,
                strings = { tab ->
                    when (tab) {
                        Tab.Home -> strings.navHome
                        Tab.Subscriptions -> strings.navSubscriptions
                        Tab.History -> strings.navHistory
                        Tab.Settings -> strings.navSettings
                    }
                },
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
    profileInitial: String,
    onSearch: () -> Unit,
    onOpenProfiles: () -> Unit,
    /** 0 when down, 1 when slid out of the way. */
    hidden: Float,
    underTopBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    // Measured rather than computed. The bar is the status inset plus 56dp plus
    // whatever `underTopBar` turns out to be, and that last term is a slot — the
    // shell cannot know it. Computing the first two and hoping was how the chip
    // row ended up half off the screen.
    var height by remember { mutableStateOf(0f) }

    // The inset is padding *around* the bar, not inside it: putting the bar's
    // fixed height first and the inset second left the clock sitting on the
    // logo. A 56dp bar under a 24dp status bar is 80dp of chrome, and both
    // numbers have to be spent.
    Box(
        modifier
            .fillMaxWidth()
            .onSizeChanged { height = it.height.toFloat() }
            // `offset`, not `graphicsLayer { translationY }`.
            //
            // Haze samples from the node's **layout** position and knows nothing
            // about a draw-time transform, so a translated surface drew its
            // glass at the place it would have been — which on a moving
            // bar meant no background at all, and the tab bar showing
            // straight through it. `offset` moves the node at placement, so the
            // position Haze reads is the position it is drawn at.
            .offset { IntOffset(0, (-hidden * height).roundToInt()) }
            .consumeTaps(),
    ) {
        // Behind everything in the bar, and only inside it. See [BarBackdrop]:
        // on iOS this is a live material sampling the feed sliding under it,
        // which is what the bar looked wrong without.
        BarBackdrop(Modifier.matchParentSize())
        Column(modifier = Modifier.fillMaxWidth()) {
        // windowInsetsTopHeight, not windowInsetsPadding. Padding on a Spacer
        // with no size of its own collapses to nothing, which is why the first
        // attempt left the clock sitting on the logo.
        Spacer(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Size.topBar)
                .padding(horizontal = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The mark: a red rounded rectangle with a white triangle in it.
            //
            // The triangle was left out on the reasoning that borrowing
            // YouTube's logo would be confusing beside the real app. Compared
            // against the web app on a phone, the bare rectangle is what reads
            // as wrong — it looks like an image that failed to load. The shape
            // is a play button, which is what every video app uses and none of
            // them owns.
            Box(
                Modifier
                    .size(width = 28.dp, height = 20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Tokens.brand),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = PlayMark,
                    contentDescription = strings.appName,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
                )
            }
            Spacer(Modifier.width(Space.md))

            // Not a field: a button shaped like one. Typing happens on the
            // search screen, whose own field lands exactly where this is — so
            // the two never appear together, and there is no inert box left
            // behind the one being typed into.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(Size.chip + 8.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable(onClick = onSearch)
                    .border(1.dp, Tokens.line, RoundedCornerShape(percent = 50)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Tokens.surfaceInput)
                        .padding(horizontal = Space.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.search, color = Tokens.text2, fontSize = 14.sp)
                }
                // The magnifier is its own segment at the right end, divided
                // from the field by a line — the web app's shape, and the thing
                // that makes the pill read as a search box rather than as an
                // empty input.
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(56.dp)
                        .background(Tokens.surfaceHover),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = SearchIcon,
                        contentDescription = strings.search,
                        tint = Tokens.text,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(Space.md))

            // The household member's initial. Data, not copy — which is why it
            // is a parameter rather than a literal, and why the untranslated
            // guard was right to object to the hard-coded "K" that was here.
            // It is empty until the profile picker exists, and an empty circle
            // is the honest drawing of "nobody has said who they are".
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    // It opens the household's members. It used to do nothing
                    // at all, which is the dead button §5 forbids, sitting in
                    // the most-looked-at corner of the app.
                    .clickable(onClick = onOpenProfiles)
                    // Coloured from the initial, the way every avatar in this
                    // app is. A grey circle beside a red logo reads as an image
                    // that has not loaded; a coloured one reads as a person.
                    .background(
                        if (profileInitial.isEmpty()) Tokens.surfaceHover
                        else avatarColourFor(profileInitial),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (profileInitial.isNotEmpty()) {
                    Text(
                        text = profileInitial.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        // After the search row, inside the same Column — so it shares the bar's
        // backdrop and the translation on the Box around both. Inside the Row it
        // sat *beside* the search field and pushed it off the screen.
        underTopBar()
        }
    }
}

@Composable
private fun BottomBar(
    current: Tab,
    onSelect: (Tab) -> Unit,
    strings: (Tab) -> String,
    hidden: Float,
    modifier: Modifier = Modifier,
) {
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val height = with(LocalDensity.current) { (navBar + Size.topBar).toPx() }

    Box(
        modifier
            .fillMaxWidth()
            .offset { IntOffset(0, (hidden * height).roundToInt()) }
            .consumeTaps(),
    ) {
        BarBackdrop(Modifier.matchParentSize(), fromTop = false)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
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
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}


/** The white triangle inside the mark. */
private val PlayMark: ImageVector by lazy {
    ImageVector.Builder(
        name = "PlayMark",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(8f, 5f); lineTo(19f, 12f); lineTo(8f, 19f); close()
        }
    }.build()
}
