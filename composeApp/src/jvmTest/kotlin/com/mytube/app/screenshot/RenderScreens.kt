package com.mytube.app.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.i18n.Strings
import com.mytube.app.ui.theme.MytubeTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * Renders a screen to a PNG, off any device.
 *
 * ## Why this exists, given there are no UI tests
 *
 * It is not a test and it asserts nothing. It is a way to *look* at a screen
 * without an emulator or a phone — the same thing `@Preview` gives inside an
 * IDE, produced as a file so it can be sent to somebody who is not sitting in
 * front of one.
 *
 * The JVM target already exists to run tests in seconds, and Compose Desktop can
 * draw the same composables the phone draws, so this costs nothing but the file
 * it writes.
 */
private val coilReady: Unit by lazy {
    // Coil needs an ImageLoader before AsyncImage will fetch anything, and off
    // Android nobody installs one. Without this every image in a screenshot is
    // the placeholder — which is exactly what the first batch showed.
    SingletonImageLoader.setSafe { context -> ImageLoader(context) }
}

class ScreenRenderer(
    private val outputDir: File,
    private val width: Int = 1080,
    private val height: Int = 2160,
    /** A phone's pixel density; 2.75 is a typical Android xxhdpi screen. */
    private val density: Float = 2.75f,
) {
    fun render(name: String, strings: Strings, content: @Composable () -> Unit) {
        coilReady
        outputDir.mkdirs()
        // **Images do not appear in these renders, and cannot.** Coil loads
        // asynchronously and ImageComposeScene draws a single frame without a
        // recomposition loop, so an AsyncImage is always still on its
        // placeholder. Two-pass rendering with a delay was tried and changes
        // nothing.
        //
        // That is a real limit and it bounds what these pictures are for:
        // layout, spacing, type, colour and copy — everything except the
        // photographs. Anything about the images is judged on a device.
        ImageComposeScene(
            width = width,
            height = height,
            density = Density(density),
        ) {
            CompositionLocalProvider(LocalStrings provides strings) {
                MytubeTheme {
                    Box(Modifier.fillMaxSize()) { content() }
                }
            }
        }.let { scene ->
            // close() in a finally rather than use(): ImageComposeScene holds a
            // Skia surface, and leaking one per screen would exhaust native
            // memory long before the JVM noticed.
            try {
                val data = scene.render().encodeToData(EncodedImageFormat.PNG)
                    ?: error("could not encode $name")
                File(outputDir, "$name.png").writeBytes(data.bytes)
            } finally {
                scene.close()
            }
        }
    }
}
