package com.mytube.app.ui.shell

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.mytube.app.R

/**
 * The same marks iOS draws, rendered once and carried here.
 *
 * # Why they are pictures rather than vectors
 *
 * They are SF Symbols, and the only place their outlines exist on this machine
 * is `Assets.car` inside `CoreGlyphs.bundle` — a compiled asset catalogue. The
 * font beside it, `SFUISymbols-Regular.otf`, holds nine glyphs, not nine
 * thousand, so there is nothing to read paths out of without SF Symbols.app.
 *
 * So each one was drawn once by AppKit at 192pt, white on nothing, and written
 * out as a PNG: the same renderer iOS uses, at four times the size this bar ever
 * draws them. `Icon` fits and tints them, so the red that crosses the bar as the
 * pill travels still costs a colour filter and no redraw.
 *
 * # The licence, stated rather than assumed
 *
 * Apple's terms allow SF Symbols *"solely for purposes of designing and
 * developing applications for Apple platforms"* — the restriction is on the
 * platform, not on whether an app is published. This app is a household client
 * that is never distributed, and the owner made that call knowing the term. It
 * is written down here so the next person reading this file is not left to
 * discover it.
 */
@Composable
actual fun TabGlyph(tab: Tab, selected: Boolean, tint: Color, modifier: Modifier) {
    Icon(
        painter = painterResource(tab.drawable(selected)),
        contentDescription = null,
        tint = tint,
        modifier = modifier,
    )
}

/** The filled pair on the tab you are on, hollow on the others — iOS's pairing. */
private fun Tab.drawable(selected: Boolean): Int = when (this) {
    Tab.Home -> if (selected) R.drawable.ic_tab_house_fill else R.drawable.ic_tab_house
    // `list.triangle` has no filled pair in the set, so this one is marked by
    // the colour and the pill alone.
    Tab.Playlists -> R.drawable.ic_tab_list_triangle
    Tab.Settings -> if (selected) R.drawable.ic_tab_gearshape_fill else R.drawable.ic_tab_gearshape
}
