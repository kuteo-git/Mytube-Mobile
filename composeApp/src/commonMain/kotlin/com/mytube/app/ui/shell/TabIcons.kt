package com.mytube.app.ui.shell

import com.mytube.app.ui.home.stroke
import com.mytube.app.ui.i18n.Strings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The tab icons, outlined.
 *
 * Outlines rather than filled shapes, matching the web app's Lucide set, and
 * written out rather than pulled from `material-icons-extended` — several
 * megabytes for four glyphs.
 */
internal fun tabIcon(tab: Tab): ImageVector = when (tab) {
    Tab.Home -> HomeIcon
    Tab.History -> HistoryTabIcon
    Tab.Playlists -> PlaylistsTabIcon
    Tab.Settings -> SettingsIcon
}

/**
 * A clock with a counter-clockwise arrow over it — the "recents" mark, as
 * YouTube and iOS both draw Watch History (`clock.arrow.circlepath`). The arrow
 * at the top says *back in time*, which a bare clock does not.
 */
private val HistoryTabIcon: ImageVector by lazy {
    stroke("History") {
        // The dial.
        moveTo(12f, 6.5f)
        curveTo(15f, 6.5f, 17.5f, 9f, 17.5f, 12f)
        curveTo(17.5f, 15f, 15f, 17.5f, 12f, 17.5f)
        curveTo(9f, 17.5f, 6.5f, 15f, 6.5f, 12f)
        // The hands.
        moveTo(12f, 9f); lineTo(12f, 12f); lineTo(14f, 13.5f)
        // The counter-clockwise arrow closing the open top-left of the dial.
        moveTo(6.5f, 12f); curveTo(6.5f, 9f, 9f, 6.5f, 12f, 6.5f)
        moveTo(4.2f, 8.5f); lineTo(6.5f, 9.2f); lineTo(7.2f, 6.9f)
    }
}

/**
 * Lines with a music note (`music.note.list`), stroked like its neighbours.
 *
 * This is the preview target's face only; the phone draws the SF Symbol (iOS)
 * or its rendered PNG (Android). It is kept as the same mark so a preview reads
 * like the device. Not `PlaylistIcon` from `ui/playlist`: that one is filled,
 * drawn for a badge over a picture, and a filled glyph beside stroked ones is
 * the third icon fault this app has had — the tab bar reads as one set or none.
 */
private val PlaylistsTabIcon: ImageVector by lazy {
    stroke("Playlists") {
        // The list.
        moveTo(3f, 7f); lineTo(12f, 7f)
        moveTo(3f, 12f); lineTo(12f, 12f)
        moveTo(3f, 17f); lineTo(9f, 17f)
        // The note: a stem with a flag, and a head at its foot.
        moveTo(16f, 16f); lineTo(16f, 6f); lineTo(20f, 8f)
        moveTo(16f, 16f)
        curveTo(16f, 17.1f, 15.1f, 18f, 14f, 18f)
        curveTo(12.9f, 18f, 12f, 17.1f, 12f, 16f)
        curveTo(12f, 14.9f, 12.9f, 14f, 14f, 14f)
        curveTo(15.1f, 14f, 16f, 14.9f, 16f, 16f)
        close()
    }
}

private val HomeIcon: ImageVector by lazy {
    stroke("Home") {
        moveTo(3f, 10f); lineTo(12f, 3f); lineTo(21f, 10f)
        lineTo(21f, 20f); lineTo(3f, 20f); close()
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

/** A tab's name, for both shells' labels — the phone's bar and the television's rail. */
fun tabLabel(tab: Tab, strings: Strings): String = when (tab) {
    Tab.Home -> strings.navHome
    Tab.History -> strings.historyTitle
    Tab.Playlists -> strings.playlists
    Tab.Settings -> strings.navSettings
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
