package com.mytube.app.ui.shell

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The three tab icons, outlined.
 *
 * Outlines rather than filled shapes, matching the web app's Lucide set, and
 * written out rather than pulled from `material-icons-extended` — several
 * megabytes for four glyphs.
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

internal fun tabIcon(tab: Tab): ImageVector = when (tab) {
    Tab.Home -> HomeIcon
    Tab.Subscriptions -> SubscriptionsIcon
    Tab.Settings -> SettingsIcon
}

private val HomeIcon: ImageVector by lazy {
    stroke("Home") {
        moveTo(3f, 10f); lineTo(12f, 3f); lineTo(21f, 10f)
        lineTo(21f, 20f); lineTo(3f, 20f); close()
    }
}

/**
 * Two people, not a stack of cards.
 *
 * The stack was a guess at "a list of things" and it is the icon for a playlist
 * or a library. Compared against the web app on a phone, that tab is **people** —
 * which is what a subscription is, and what makes it read differently from
 * History beside it.
 */
private val SubscriptionsIcon: ImageVector by lazy {
    stroke("Subscriptions") {
        // The person in front.
        moveTo(9.5f, 11.5f)
        curveTo(11.2f, 11.5f, 12.5f, 10.2f, 12.5f, 8.5f)
        curveTo(12.5f, 6.8f, 11.2f, 5.5f, 9.5f, 5.5f)
        curveTo(7.8f, 5.5f, 6.5f, 6.8f, 6.5f, 8.5f)
        curveTo(6.5f, 10.2f, 7.8f, 11.5f, 9.5f, 11.5f)
        close()
        moveTo(2.5f, 19f)
        curveTo(2.5f, 15.7f, 5.6f, 13.5f, 9.5f, 13.5f)
        curveTo(13.4f, 13.5f, 16.5f, 15.7f, 16.5f, 19f)
        // The one behind, drawn only where it shows past the first.
        moveTo(16f, 5.9f)
        curveTo(17.5f, 6.3f, 18.5f, 7.6f, 18.5f, 9f)
        curveTo(18.5f, 10.4f, 17.6f, 11.6f, 16.3f, 12.1f)
        moveTo(18.2f, 14.3f)
        curveTo(20.2f, 15f, 21.5f, 16.6f, 21.5f, 19f)
    }
}

/**
 * A cog, not a sun.
 *
 * The spokes-and-circle version read as brightness — which is what it is in
 * every other app that draws it. Compared against the web app, Settings is a
 * gear: a ring with teeth, drawn as eight short radial strokes at the rim rather
 * than lines running out into space.
 */
private val SettingsIcon: ImageVector by lazy {
    stroke("Settings") {
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
        // The teeth: short strokes crossing the rim, not rays leaving it.
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

/** The magnifier in the search field's own segment, as the web app draws it. */
internal val SearchIcon: ImageVector by lazy {
    stroke("Search") {
        moveTo(11f, 4f)
        curveTo(14.9f, 4f, 18f, 7.1f, 18f, 11f)
        curveTo(18f, 14.9f, 14.9f, 18f, 11f, 18f)
        curveTo(7.1f, 18f, 4f, 14.9f, 4f, 11f)
        curveTo(4f, 7.1f, 7.1f, 4f, 11f, 4f)
        close()
        moveTo(16.2f, 16.2f); lineTo(21f, 21f)
    }
}
