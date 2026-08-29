package com.mytube.app.screenshot

import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.Video
import com.mytube.app.ui.home.HomeContent
import com.mytube.app.ui.home.HomeState
import com.mytube.app.ui.i18n.EnglishStrings
import com.mytube.app.ui.i18n.VietnameseStrings
import com.mytube.app.ui.settings.CheckState
import com.mytube.app.ui.settings.ServerSetupContent
import com.mytube.app.ui.settings.ServerSetupState
import java.io.File
import kotlin.test.Test

/**
 * Writes a PNG of every screen, in both languages.
 *
 * A `@Test` only because that is the cheapest way to run JVM code with the
 * project's classpath; it asserts nothing and cannot fail a build for what a
 * screen looks like. That is deliberate — screenshots here are for **looking
 * at**, not for comparing against a stored image. Golden-image tests were
 * explicitly not wanted, and a picture that fails a build because a font
 * rendered a pixel differently is the reason.
 *
 * Run with:  ./gradlew jvmTest --tests '*Screenshots*'
 * Output in: composeApp/build/screenshots/
 */
class Screenshots {

    private val renderer = ScreenRenderer(File("build/screenshots"))

    /**
     * The real library on disk, so the pictures show real thumbnails.
     *
     * A `file://` base rather than the gateway, because the screenshots must
     * render whether or not the stack happens to be running, and the images are
     * the same bytes either way.
     */
    private val mediaBase = "file:///Volumes/Data2/Youtube"

    @Test
    fun renderEveryScreen() {
        // --- server setup ---------------------------------------------------
        renderer.render("setup-empty", EnglishStrings) {
            ServerSetupContent(ServerSetupState(), {}, {}, {})
        }
        renderer.render("setup-found", EnglishStrings) {
            ServerSetupContent(
                ServerSetupState("10.25.113.151:8180", CheckState.Reachable),
                {}, {}, {},
            )
        }
        // The failure line is the longest string in the app, and it is longer
        // again in Vietnamese — the case most likely to wrap badly.
        renderer.render("setup-failed-vi", VietnameseStrings) {
            ServerSetupContent(
                ServerSetupState("10.25.113.9:8180", CheckState.Failed),
                {}, {}, {},
            )
        }

        // --- home -----------------------------------------------------------
        renderer.render("home-feed", EnglishStrings) {
            HomeContent(HomeState.Ready(feed(), "t1"), mediaBase, {}, {}, {})
        }
        renderer.render("home-feed-vi", VietnameseStrings) {
            HomeContent(HomeState.Ready(feed(), "t1"), mediaBase, {}, {}, {})
        }
        renderer.render("home-no-server", EnglishStrings) {
            HomeContent(HomeState.NeedsServer, "", {}, {}, {})
        }
        renderer.render("home-failed-vi", VietnameseStrings) {
            HomeContent(HomeState.Failed("gateway answered 502"), "", {}, {}, {})
        }
    }

    /**
     * Titles and numbers copied from a real `/api/feed` response.
     *
     * Invented sample data flatters a layout: real titles are longer than
     * anybody writing a mock would choose, and the two-line clamp only shows its
     * behaviour against one.
     */
    private fun feed() = listOf(
        video(
            "LkVLxE0B7P8",
            "Canadian describes what he saw as catastrophic floods hit Nepal | Hanomansing Tonight",
            "CBC News", "UCuFFtHWoLl5fauMMD5Ww2jA", 451, 157_000,
        ),
        video(
            "Kidu9qrRV6o",
            "Eminem — Full Dark Symphonic Orchestra Cover | Cinematic & Intense",
            "ORKESTRAL", "UCP9BFw4FQjXrqioylAfsUnQ", 218, 40_800,
        ),
        video(
            "RPnVqjWXkUw",
            "Three hours of something, and a title long enough to be cut off after two lines on a phone screen",
            "NASA", "UCLA_DiR1FfKNvjuUpBHmylQ", 10_801, 4_730_000,
        ),
    )

    private fun video(
        id: String,
        title: String,
        channel: String,
        channelId: String,
        seconds: Int,
        views: Long,
    ) = Video(
        id = id,
        title = title,
        channel = Channel(
            id = channelId,
            name = channel,
            handle = "@$channel",
            avatarPath = "channels/$channelId/avatar.jpg",
        ),
        durationSeconds = seconds,
        viewCount = views,
        publishedAt = "2026-08-28T05:35:52Z",
        thumbnailPath = "thumbnails/$id.jpg",
    )
}
