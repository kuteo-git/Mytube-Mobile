package com.mytube.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mytube.app.ui.theme.Tokens
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * The material behind a bottom sheet.
 *
 * The same Haze source the bars read — which works here for the reason it works
 * there and would **not** have worked behind a native iOS sheet: a Material
 * `ModalBottomSheet` is drawn inside this app's own Compose scene, over the
 * content that Haze has registered.
 *
 * That is why the sheets are Material's on both platforms rather than Calf's.
 * `AdaptiveBottomSheet` presents a real `UISheetPresentationController` on iOS —
 * a separate Compose scene in its own view controller — which is genuinely more
 * native and was tried: on the simulator the gear and the avatar both consumed
 * their tap and **no sheet appeared**. Unverified-and-broken beats
 * less-native-and-working nowhere, so this reverted. A separate scene also puts
 * the sheet out of Haze's reach, so the blur asked for here would have needed a
 * second, different mechanism.
 *
 * Calf still supplies the switch, the press feedback and the spinner, which are
 * in the same scene and do work.
 */
@Composable
fun SheetBackdrop(modifier: Modifier = Modifier) {
    val haze = LocalHaze.current
    if (haze == null) {
        Box(modifier.background(Tokens.bg))
        return
    }
    Box(
        modifier.hazeEffect(state = haze) {
            blurRadius = 32.dp
            // Heavier than the bars'. A sheet covers a third of the screen and
            // carries rows of text; the bars carry a few glyphs over a strip.
            tints = listOf(HazeTint(Tokens.bg.copy(alpha = 0.7f)))
        },
    )
}
