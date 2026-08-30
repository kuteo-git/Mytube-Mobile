package com.mytube.app.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import com.mytube.app.ui.theme.Tokens
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * The material a bottom sheet is made of.
 *
 * ## Why a Modifier and not a composable behind the content
 *
 * It was a `Box(Modifier.matchParentSize())` *inside* the sheet, and
 * `matchParentSize` matched the sheet's **content column**, not the sheet. A
 * sheet is taller than what is in it: there is a drag handle above, and the
 * navigation inset below. Both were left unpainted, so the strip under the last
 * row was a window straight through to the comments behind — which is exactly
 * what the sheet was reported as having, an empty gap at the bottom.
 *
 * Applied to the sheet's own modifier there is nothing left over to miss.
 *
 * ## Why the blur works here when it does not behind the floating bars
 *
 * A Material `ModalBottomSheet` is drawn inside this app's own Compose scene,
 * over the content Haze has registered. See [BarBackdrop] for the two ways this
 * fails when the surface is not in that scene.
 *
 * ## Why this is not Calf's sheet
 *
 * `AdaptiveBottomSheet` presents a real `UISheetPresentationController` on iOS,
 * which is more native and was tried: on the simulator the gear and the avatar
 * both consumed their tap and no sheet appeared. It also puts the sheet in a
 * separate Compose scene, out of Haze's reach.
 */
@Composable
fun Modifier.sheetBackdrop(): Modifier {
    val haze = LocalHaze.current
    if (haze == null) return drawBehind { drawRect(Tokens.bg) }
    return hazeEffect(state = haze) {
        blurRadius = 32.dp
        // Heavier than the bars'. A sheet covers a third of the screen and
        // carries rows of text; a bar carries a few glyphs over a strip.
        tints = listOf(HazeTint(Tokens.bg.copy(alpha = 0.7f)))
    }
}
