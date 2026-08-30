package com.mytube.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mytube.app.domain.repository.FeedMix
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.theme.Tokens

/**
 * The three sliders that divide Home.
 *
 * ## Why they are normalised rather than clamped
 *
 * The server takes three numbers that must total 100. Clamping each slider to
 * "100 minus the other two" makes the last few percent unreachable — a slider
 * that stops moving before its end is one people push at. Instead each slider
 * takes what it is given and the other two are rescaled to fill the rest, which
 * is what the web app does and what makes all three reachable.
 *
 * ## Why the fixed share is shown and not assumed
 *
 * `fixedPercent` comes from the server. The web app carried its own copy once
 * and spent a release quoting a stale figure after a new fixed share took ten
 * per cent of the page.
 */
@Composable
fun FeedMixSection(mix: FeedMix, onChange: (FeedMix) -> Unit, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current

    Column(modifier.fillMaxWidth().padding(horizontal = Space.lg)) {
        Text(
            text = strings.feedMixHint,
            color = Tokens.text2,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(Space.md))

        MixSlider(strings.feedMixSubscribed, mix.subscribedPercent) {
            onChange(rebalance(mix, subscribed = it))
        }
        MixSlider(strings.feedMixAffinity, mix.affinityPercent) {
            onChange(rebalance(mix, affinity = it))
        }
        MixSlider(strings.feedMixDiscovery, mix.discoveryPercent) {
            onChange(rebalance(mix, discovery = it))
        }
    }
}

@Composable
private fun MixSlider(label: String, percent: Int, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = Space.md)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, color = Tokens.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(
                text = "$percent%",
                color = Tokens.text2,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Slider(
            value = percent.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = Tokens.brand,
                activeTrackColor = Tokens.brand,
                inactiveTrackColor = Tokens.surfaceHover,
            ),
        )
    }
}

/**
 * Move one share and rescale the other two to fill what is left.
 *
 * Pure, so the arithmetic can be tested without a screen — and it needs testing,
 * because the two edges are where it goes wrong: moving one slider to 100 leaves
 * nothing to rescale, and rounding three integers to a total of 100 does not
 * come out even.
 */
internal fun rebalance(
    mix: FeedMix,
    subscribed: Int? = null,
    affinity: Int? = null,
    discovery: Int? = null,
): FeedMix {
    val moved = (subscribed ?: affinity ?: discovery ?: 0).coerceIn(0, 100)
    val remaining = 100 - moved

    // The two that did not move, and what they were worth relative to each other.
    val (a, b) = when {
        subscribed != null -> mix.affinityPercent to mix.discoveryPercent
        affinity != null -> mix.subscribedPercent to mix.discoveryPercent
        else -> mix.subscribedPercent to mix.affinityPercent
    }
    val total = a + b
    // Split evenly when there is nothing to scale. Dividing by zero would be a
    // crash; leaving both at zero would silently lose the remainder, and the
    // three would no longer total 100.
    val newA = if (total == 0) remaining / 2 else (remaining * a) / total
    val newB = remaining - newA

    return when {
        subscribed != null -> mix.copy(
            subscribedPercent = moved,
            affinityPercent = newA,
            discoveryPercent = newB,
        )
        affinity != null -> mix.copy(
            affinityPercent = moved,
            subscribedPercent = newA,
            discoveryPercent = newB,
        )
        else -> mix.copy(
            discoveryPercent = moved,
            subscribedPercent = newA,
            affinityPercent = newB,
        )
    }
}
