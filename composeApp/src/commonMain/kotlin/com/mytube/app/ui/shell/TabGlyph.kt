package com.mytube.app.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * A tab's mark, drawn by whichever platform is showing it.
 *
 * # Why this is a seam and not one vector
 *
 * Every other icon in this app is one `ImageVector` shared by both platforms,
 * and that is right for a bookmark or a thumb. A tab bar is the one place a
 * phone's own vocabulary shows, and iOS has a vocabulary: **SF Symbols**, with a
 * hollow mark for the tab you are not on and a filled one for the tab you are.
 *
 * It cannot be shared, and the reason is a licence rather than a preference.
 * Apple's terms allow SF Symbols *"solely for purposes of designing and
 * developing applications for Apple platforms"* — they may not be embedded in
 * an Android app, and redrawing them to get around that is the thing the term
 * is aimed at. So iOS asks `UIImage` for the symbol and Android keeps the
 * vectors this app drew.
 *
 * The cost is the one this charter has already paid three times over icons: two
 * sets have to agree about what each mark *means*. A stack of cards once meant
 * a playlist here and a sun once meant brightness. The names are chosen to be
 * the obvious translation of each vector and are written down beside them.
 */
@Composable
expect fun TabGlyph(
    tab: Tab,
    /** Filled on the tab you are on, hollow on the others — iOS's own pairing. */
    selected: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
)
