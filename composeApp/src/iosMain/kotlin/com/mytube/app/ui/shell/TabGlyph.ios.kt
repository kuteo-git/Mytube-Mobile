package com.mytube.app.ui.shell

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UIImageSymbolConfiguration
import platform.UIKit.UIImageRenderingMode
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy

/**
 * The system's own mark, asked for by name.
 *
 * `UIImage.systemImageNamed` is the whole of it: the symbol is already on the
 * phone, drawn by the same renderer the rest of iOS uses, at whatever weight and
 * size is asked for. Nothing is bundled, which is also what keeps this inside
 * Apple's terms — the symbols are used on an Apple platform and travel nowhere.
 *
 * # Why it goes through a PNG
 *
 * Compose draws Skia images, and a `UIImage` is a CoreGraphics one. There is no
 * shared representation, so the picture is rendered once, encoded, and decoded
 * into a Skia image. It is not free, which is why it is remembered on the three
 * things that change it — the name, the size and the density — rather than on
 * every frame. The tint is not one of them: the symbol is rendered as a
 * template and `Icon` colours it, so the red crossing the bar as the pill
 * travels costs a colour filter and no redraw.
 *
 * A symbol that does not exist comes back null, and then nothing is drawn — an
 * empty slot rather than a crash on a phone running an iOS whose symbol set is
 * older than the name asked for.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun TabGlyph(tab: Tab, selected: Boolean, tint: Color, modifier: Modifier) {
    val name = tab.symbol(selected)
    val density = LocalDensity.current.density
    val bitmap: ImageBitmap? = remember(name, density) { symbolBitmap(name, density) }
    if (bitmap != null) {
        Icon(bitmap = bitmap, contentDescription = null, tint = tint, modifier = modifier)
    }
}

/**
 * The names, and the vector each one translates.
 *
 * Chosen to be the obvious reading of the mark Android draws, because two icon
 * sets in one app have to agree about meaning — this charter records a stack of
 * cards that meant "playlist" and a sun that meant "brightness", both wrong and
 * both found on a phone.
 */
private fun Tab.symbol(selected: Boolean): String = when (this) {
    // A house. `house.fill` is the filled pair iOS itself uses for a tab bar.
    Tab.Home -> if (selected) "house.fill" else "house"
    // Lines with a play triangle: the queue, not a stack of records.
    Tab.Playlists -> if (selected) "list.triangle" else "list.triangle"
    // A cog. `gearshape` rather than `gear`, which is the older heavier one.
    Tab.Settings -> if (selected) "gearshape.fill" else "gearshape"
}

@OptIn(ExperimentalForeignApi::class)
private fun symbolBitmap(name: String, density: Float): ImageBitmap? {
    // 24 points, the size every other glyph in this bar is drawn at, asked for
    // in points and rendered by UIKit at the screen's own scale.
    val configuration = UIImageSymbolConfiguration.configurationWithPointSize(GLYPH_POINTS)
    val symbol = UIImage.systemImageNamed(name, configuration) ?: return null
    // White and template, so `Icon`'s tint is the only thing deciding its
    // colour — including while that colour is animating.
    val template = symbol
        .imageWithTintColor(UIColor.whiteColor, UIImageRenderingMode.UIImageRenderingModeAlwaysOriginal)
    val png = UIImagePNGRepresentation(template) ?: return null
    return png.toByteArray()?.let { Image.makeFromEncoded(it).toComposeImageBitmap() }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray? {
    val size = length.toInt()
    if (size == 0) return null
    val bytes = ByteArray(size)
    bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), this.bytes, this.length) }
    return bytes
}

private const val GLYPH_POINTS = 24.0
