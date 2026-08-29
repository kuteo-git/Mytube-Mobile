package com.mytube.app.ui.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The handful of icons this app draws, as vectors.
 *
 * Material's icon library is a dependency of several megabytes for a few shapes,
 * and `material-icons-extended` is worse. These are the same paths, written out
 * once. When the set outgrows a screenful, that is the moment to reconsider —
 * not before.
 */
private fun icon(name: String, pathData: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) { pathData() }
    }.build()

val MoreVertical: ImageVector by lazy {
    icon("MoreVertical") {
        moveTo(12f, 8f)
        curveTo(13.1f, 8f, 14f, 7.1f, 14f, 6f)
        curveTo(14f, 4.9f, 13.1f, 4f, 12f, 4f)
        curveTo(10.9f, 4f, 10f, 4.9f, 10f, 6f)
        curveTo(10f, 7.1f, 10.9f, 8f, 12f, 8f)
        close()
        moveTo(12f, 10f)
        curveTo(10.9f, 10f, 10f, 10.9f, 10f, 12f)
        curveTo(10f, 13.1f, 10.9f, 14f, 12f, 14f)
        curveTo(13.1f, 14f, 14f, 13.1f, 14f, 12f)
        curveTo(14f, 10.9f, 13.1f, 10f, 12f, 10f)
        close()
        moveTo(12f, 16f)
        curveTo(10.9f, 16f, 10f, 16.9f, 10f, 18f)
        curveTo(10f, 19.1f, 10.9f, 20f, 12f, 20f)
        curveTo(13.1f, 20f, 14f, 19.1f, 14f, 18f)
        curveTo(14f, 16.9f, 13.1f, 16f, 12f, 16f)
        close()
    }
}
