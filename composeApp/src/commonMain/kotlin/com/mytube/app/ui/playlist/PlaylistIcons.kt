package com.mytube.app.ui.playlist

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The three shapes playlists need, written out like the rest.
 *
 * `ui/home/Icons.kt` states the rule these follow: Material's icon library is
 * megabytes for a handful of paths. When the set outgrows a screenful the
 * dependency is worth reconsidering — it has not yet.
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

/** Three lines with a play triangle — YouTube's mark for a collection. */
val PlaylistIcon: ImageVector by lazy {
    icon("Playlist") {
        moveTo(3f, 6f); lineTo(15f, 6f); lineTo(15f, 8f); lineTo(3f, 8f); close()
        moveTo(3f, 11f); lineTo(15f, 11f); lineTo(15f, 13f); lineTo(3f, 13f); close()
        moveTo(3f, 16f); lineTo(11f, 16f); lineTo(11f, 18f); lineTo(3f, 18f); close()
        moveTo(16f, 11f); lineTo(22f, 14.5f); lineTo(16f, 18f); close()
    }
}

val PlusIcon: ImageVector by lazy {
    icon("Plus") {
        moveTo(11f, 5f); lineTo(13f, 5f); lineTo(13f, 11f); lineTo(19f, 11f)
        lineTo(19f, 13f); lineTo(13f, 13f); lineTo(13f, 19f); lineTo(11f, 19f)
        lineTo(11f, 13f); lineTo(5f, 13f); lineTo(5f, 11f); lineTo(11f, 11f); close()
    }
}

/** The saved shelf's own mark, the same bookmark the watch screen's pill uses. */
val BookmarkIcon: ImageVector by lazy {
    icon("Bookmark") {
        moveTo(6f, 3f); lineTo(18f, 3f); lineTo(18f, 21f); lineTo(12f, 16.5f)
        lineTo(6f, 21f); close()
    }
}

/** A filled triangle, for "play all". */
val PlayIcon: ImageVector by lazy {
    icon("Play") {
        moveTo(7f, 4f); lineTo(20f, 12f); lineTo(7f, 20f); close()
    }
}
