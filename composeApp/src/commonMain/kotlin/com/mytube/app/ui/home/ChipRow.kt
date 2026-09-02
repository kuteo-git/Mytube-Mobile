package com.mytube.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.shell.GlassRadius
import com.mytube.app.ui.shell.pressableLiquidGlass
import com.mytube.app.ui.theme.Tokens

/**
 * The filter chips above the feed.
 *
 * Height 32, radius 8, padding 0 12 — from the design system, like everything
 * else. The selected chip inverts (`--invert-bg` on `--invert-text`), which is
 * the one place in this app where a light surface appears on purpose.
 */
@Composable
fun ChipRow(
    chips: List<Chip>,
    selected: Chip,
    onSelect: (Chip) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current

    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Space.md),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        items(chips, key = { it.key }) { chip ->
            val isSelected = chip == selected
            Row(
                modifier = Modifier
                    .height(Size.chip)
                    .pressableLiquidGlass(GlassRadius.control, selected = isSelected) {
                        onSelect(chip)
                    }
                    .padding(horizontal = Space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (chip == Chip.Live) {
                    // A red dot, not an animated one. The web app's note on this
                    // is worth keeping: a blinking dot "takes the word beside it
                    // with it and reads as a fault light, in a row that is
                    // scanned rather than stared at."
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Tokens.brand),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = when (chip) {
                        Chip.All -> strings.chipAll
                        Chip.Missed -> strings.chipMissed
                        Chip.Live -> strings.chipLive
                        is Chip.Category -> chip.topic.name
                    },
                    color = if (isSelected) Tokens.invertText else Tokens.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
