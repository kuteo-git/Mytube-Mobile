package com.mytube.app.ui.watch

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The watch screen's glyphs, drawn rather than imported.
 *
 * Same reasoning as `TabIcons`: `material-icons-extended` is several megabytes
 * for a handful of shapes, and the app's set is Lucide's outlines rather than
 * Material's filled ones — mixing the two is visible in a row.
 *
 * Play and pause are the exception and are **filled**. They sit over a moving
 * picture rather than on a surface, and an outline there reads as a shape the
 * video is showing through.
 */
private fun stroke(name: String, body: PathBuilder.() -> Unit) =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) { body() }
    }.build()

private fun filled(name: String, body: PathBuilder.() -> Unit) =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) { body() }
    }.build()

internal val PlayIcon: ImageVector by lazy {
    filled("Play") {
        moveTo(7f, 4f); lineTo(20f, 12f); lineTo(7f, 20f); close()
    }
}

internal val PauseIcon: ImageVector by lazy {
    filled("Pause") {
        moveTo(7f, 4f); lineTo(11f, 4f); lineTo(11f, 20f); lineTo(7f, 20f); close()
        moveTo(13f, 4f); lineTo(17f, 4f); lineTo(17f, 20f); lineTo(13f, 20f); close()
    }
}

/**
 * A circling arrow with the jump written inside it, as every player draws it.
 *
 * The number is not in the glyph — it is drawn beside it — because the same
 * shape mirrored serves both directions, and baking "10" into a path means two
 * paths that have to be kept in step.
 */
internal val SkipBackIcon: ImageVector by lazy {
    stroke("SkipBack") {
        moveTo(3f, 12f)
        curveTo(3f, 7f, 7f, 3.5f, 12f, 3.5f)
        curveTo(17f, 3.5f, 21f, 7f, 21f, 12f)
        curveTo(21f, 17f, 17f, 20.5f, 12f, 20.5f)
        curveTo(8.5f, 20.5f, 5.5f, 18.8f, 4f, 16.2f)
        moveTo(3f, 12f); lineTo(6.5f, 9.5f)
        moveTo(3f, 12f); lineTo(0.5f, 15f)
    }
}

internal val SkipForwardIcon: ImageVector by lazy {
    stroke("SkipForward") {
        moveTo(21f, 12f)
        curveTo(21f, 7f, 17f, 3.5f, 12f, 3.5f)
        curveTo(7f, 3.5f, 3f, 7f, 3f, 12f)
        curveTo(3f, 17f, 7f, 20.5f, 12f, 20.5f)
        curveTo(15.5f, 20.5f, 18.5f, 18.8f, 20f, 16.2f)
        moveTo(21f, 12f); lineTo(17.5f, 9.5f)
        moveTo(21f, 12f); lineTo(23.5f, 15f)
    }
}

internal val BackIcon: ImageVector by lazy {
    stroke("Back") {
        moveTo(20f, 12f); lineTo(4f, 12f)
        moveTo(10f, 6f); lineTo(4f, 12f); lineTo(10f, 18f)
    }
}

/** A thumb, up. Mirrored vertically for the other one. */
internal val ThumbIcon: ImageVector by lazy {
    stroke("Thumb") {
        moveTo(7f, 21f); lineTo(7f, 10f); lineTo(12f, 3f)
        curveTo(13.5f, 3f, 14.5f, 4.2f, 14.2f, 5.6f)
        lineTo(13.4f, 9.4f)
        lineTo(19f, 9.4f)
        curveTo(20.4f, 9.4f, 21.4f, 10.8f, 21f, 12.2f)
        lineTo(19.2f, 18.8f)
        curveTo(18.9f, 20.1f, 17.8f, 21f, 16.5f, 21f)
        close()
        moveTo(7f, 10f); lineTo(3f, 10f); lineTo(3f, 21f); lineTo(7f, 21f)
    }
}

/**
 * The same thumb, filled.
 *
 * The lit state has to be a change of *shape*, not of background. `surface` and
 * `surfaceHover` are 0x212121 and 0x272727 — six units apart, which is a
 * difference the design system uses for a pointer hovering and which is
 * invisible as a state. Measured on the emulator: pressing Like set the
 * reaction on the server and looked like nothing had happened.
 */
internal val ThumbFilledIcon: ImageVector by lazy {
    filled("ThumbFilled") {
        moveTo(7f, 21f); lineTo(7f, 10f); lineTo(12f, 3f)
        curveTo(13.5f, 3f, 14.5f, 4.2f, 14.2f, 5.6f)
        lineTo(13.4f, 9.4f)
        lineTo(19f, 9.4f)
        curveTo(20.4f, 9.4f, 21.4f, 10.8f, 21f, 12.2f)
        lineTo(19.2f, 18.8f)
        curveTo(18.9f, 20.1f, 17.8f, 21f, 16.5f, 21f)
        close()
        moveTo(2f, 10f); lineTo(6f, 10f); lineTo(6f, 21f); lineTo(2f, 21f); close()
    }
}

internal val CloseIcon: ImageVector by lazy {
    stroke("Close") {
        moveTo(6f, 6f); lineTo(18f, 18f)
        moveTo(18f, 6f); lineTo(6f, 18f)
    }
}

/** Four corners pushing outward: fill the screen. */
internal val ExpandIcon: ImageVector by lazy {
    stroke("Expand") {
        moveTo(9f, 4f); lineTo(4f, 4f); lineTo(4f, 9f)
        moveTo(15f, 4f); lineTo(20f, 4f); lineTo(20f, 9f)
        moveTo(20f, 15f); lineTo(20f, 20f); lineTo(15f, 20f)
        moveTo(9f, 20f); lineTo(4f, 20f); lineTo(4f, 15f)
    }
}

/** The same corners pulled in: give the screen back. */
internal val ShrinkIcon: ImageVector by lazy {
    stroke("Shrink") {
        moveTo(4f, 9f); lineTo(9f, 9f); lineTo(9f, 4f)
        moveTo(20f, 9f); lineTo(15f, 9f); lineTo(15f, 4f)
        moveTo(15f, 20f); lineTo(15f, 15f); lineTo(20f, 15f)
        moveTo(4f, 15f); lineTo(9f, 15f); lineTo(9f, 20f)
    }
}

/** The gear in the control bar: what belongs to *this* video. */
internal val SettingsGearIcon: ImageVector by lazy {
    stroke("PlayerSettings") {
        moveTo(12f, 9.2f)
        curveTo(10.5f, 9.2f, 9.2f, 10.5f, 9.2f, 12f)
        curveTo(9.2f, 13.5f, 10.5f, 14.8f, 12f, 14.8f)
        curveTo(13.5f, 14.8f, 14.8f, 13.5f, 14.8f, 12f)
        curveTo(14.8f, 10.5f, 13.5f, 9.2f, 12f, 9.2f)
        close()
        moveTo(12f, 4.2f)
        curveTo(16.3f, 4.2f, 19.8f, 7.7f, 19.8f, 12f)
        curveTo(19.8f, 16.3f, 16.3f, 19.8f, 12f, 19.8f)
        curveTo(7.7f, 19.8f, 4.2f, 16.3f, 4.2f, 12f)
        curveTo(4.2f, 7.7f, 7.7f, 4.2f, 12f, 4.2f)
        close()
        moveTo(12f, 2.6f); lineTo(12f, 5.4f)
        moveTo(12f, 18.6f); lineTo(12f, 21.4f)
        moveTo(2.6f, 12f); lineTo(5.4f, 12f)
        moveTo(18.6f, 12f); lineTo(21.4f, 12f)
        moveTo(5.4f, 5.4f); lineTo(7.3f, 7.3f)
        moveTo(16.7f, 16.7f); lineTo(18.6f, 18.6f)
        moveTo(18.6f, 5.4f); lineTo(16.7f, 7.3f)
        moveTo(7.3f, 16.7f); lineTo(5.4f, 18.6f)
    }
}

/** A speaker with sound coming out of it: the Vietnamese voice. */
internal val SpeakerIcon: ImageVector by lazy {
    stroke("Speaker") {
        moveTo(4f, 9f); lineTo(7f, 9f); lineTo(11.5f, 5f); lineTo(11.5f, 19f)
        lineTo(7f, 15f); lineTo(4f, 15f); close()
        moveTo(15f, 9.5f); curveTo(16.3f, 10.8f, 16.3f, 13.2f, 15f, 14.5f)
        moveTo(17.8f, 6.7f); curveTo(20.7f, 9.6f, 20.7f, 14.4f, 17.8f, 17.3f)
    }
}

internal val SpeakerFilledIcon: ImageVector by lazy {
    filled("SpeakerFilled") {
        moveTo(4f, 9f); lineTo(7f, 9f); lineTo(11.5f, 5f); lineTo(11.5f, 19f)
        lineTo(7f, 15f); lineTo(4f, 15f); close()
        moveTo(14.2f, 8.6f); lineTo(15.8f, 10.2f)
        curveTo(16.6f, 11.2f, 16.6f, 12.8f, 15.8f, 13.8f)
        lineTo(14.2f, 15.4f)
        curveTo(15.9f, 13.6f, 15.9f, 10.4f, 14.2f, 8.6f)
        close()
    }
}

/** Three nodes joined by two lines: share. */
internal val ShareIcon: ImageVector by lazy {
    stroke("Share") {
        moveTo(18f, 4.2f)
        curveTo(19.2f, 4.2f, 20.2f, 5.2f, 20.2f, 6.4f)
        curveTo(20.2f, 7.6f, 19.2f, 8.6f, 18f, 8.6f)
        curveTo(16.8f, 8.6f, 15.8f, 7.6f, 15.8f, 6.4f)
        curveTo(15.8f, 5.2f, 16.8f, 4.2f, 18f, 4.2f)
        close()
        moveTo(6f, 9.8f)
        curveTo(7.2f, 9.8f, 8.2f, 10.8f, 8.2f, 12f)
        curveTo(8.2f, 13.2f, 7.2f, 14.2f, 6f, 14.2f)
        curveTo(4.8f, 14.2f, 3.8f, 13.2f, 3.8f, 12f)
        curveTo(3.8f, 10.8f, 4.8f, 9.8f, 6f, 9.8f)
        close()
        moveTo(18f, 15.4f)
        curveTo(19.2f, 15.4f, 20.2f, 16.4f, 20.2f, 17.6f)
        curveTo(20.2f, 18.8f, 19.2f, 19.8f, 18f, 19.8f)
        curveTo(16.8f, 19.8f, 15.8f, 18.8f, 15.8f, 17.6f)
        curveTo(15.8f, 16.4f, 16.8f, 15.4f, 18f, 15.4f)
        close()
        moveTo(8f, 11f); lineTo(16f, 7.4f)
        moveTo(8f, 13f); lineTo(16f, 16.6f)
    }
}

/** A chevron pointing down. Rotated where it needs to point elsewhere. */
internal val ChevronIcon: ImageVector by lazy {
    stroke("Chevron") {
        moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f)
    }
}

/** A triangle against a bar: the next video. */
internal val NextIcon: ImageVector by lazy {
    filled("Next") {
        moveTo(5f, 5f); lineTo(15f, 12f); lineTo(5f, 19f); close()
        moveTo(17f, 5f); lineTo(19.5f, 5f); lineTo(19.5f, 19f); lineTo(17f, 19f); close()
    }
}

/** A bookmark: Save, which keeps the file against the eviction sweep. */
internal val SaveFilledIcon: ImageVector by lazy {
    filled("SaveFilled") {
        moveTo(6f, 3f); lineTo(18f, 3f); lineTo(18f, 21f); lineTo(12f, 16.5f)
        lineTo(6f, 21f); close()
    }
}

internal val SaveIcon: ImageVector by lazy {
    stroke("Save") {
        moveTo(6f, 3f); lineTo(18f, 3f); lineTo(18f, 21f); lineTo(12f, 16.5f)
        lineTo(6f, 21f); close()
    }
}
