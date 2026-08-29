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
 * The four tab icons, outlined.
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
    Tab.History -> HistoryIcon
    Tab.Settings -> SettingsIcon
}

private val HomeIcon: ImageVector by lazy {
    stroke("Home") {
        moveTo(3f, 10f); lineTo(12f, 3f); lineTo(21f, 10f)
        lineTo(21f, 20f); lineTo(3f, 20f); close()
    }
}

private val SubscriptionsIcon: ImageVector by lazy {
    stroke("Subscriptions") {
        moveTo(4f, 7f); lineTo(20f, 7f)
        moveTo(6f, 4f); lineTo(18f, 4f)
        moveTo(4f, 11f); lineTo(20f, 11f); lineTo(20f, 20f); lineTo(4f, 20f); close()
    }
}

private val HistoryIcon: ImageVector by lazy {
    stroke("History") {
        moveTo(12f, 3f)
        curveTo(7f, 3f, 3f, 7f, 3f, 12f)
        curveTo(3f, 17f, 7f, 21f, 12f, 21f)
        curveTo(17f, 21f, 21f, 17f, 21f, 12f)
        curveTo(21f, 7f, 17f, 3f, 12f, 3f)
        close()
        moveTo(12f, 7f); lineTo(12f, 12f); lineTo(15.5f, 14f)
    }
}

private val SettingsIcon: ImageVector by lazy {
    stroke("Settings") {
        moveTo(12f, 9f)
        curveTo(10.3f, 9f, 9f, 10.3f, 9f, 12f)
        curveTo(9f, 13.7f, 10.3f, 15f, 12f, 15f)
        curveTo(13.7f, 15f, 15f, 13.7f, 15f, 12f)
        curveTo(15f, 10.3f, 13.7f, 9f, 12f, 9f)
        close()
        moveTo(12f, 2f); lineTo(12f, 5f)
        moveTo(12f, 19f); lineTo(12f, 22f)
        moveTo(2f, 12f); lineTo(5f, 12f)
        moveTo(19f, 12f); lineTo(22f, 12f)
        moveTo(5f, 5f); lineTo(7f, 7f)
        moveTo(17f, 17f); lineTo(19f, 19f)
        moveTo(19f, 5f); lineTo(17f, 7f)
        moveTo(7f, 17f); lineTo(5f, 19f)
    }
}
