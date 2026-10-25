package com.mytube.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The manifest's promises that no type can hold.
 *
 * # Why this exists
 *
 * Measured on the household's Chromecast: Home, then the app's tile again, and
 * the task held **two** `MainActivity`s. The launcher's intent did not match the
 * one the task was started with, so Android stacked a second activity on the
 * first — and each activity composes its own player, with its own pair of
 * narration speakers listening to the one shared video. Pressing play started
 * both: two voices reading the same line, and switching narration off silenced
 * only the new one. Reported exactly that way.
 *
 * This app is one activity holding one sitting. A second instance is never
 * right, so the manifest says so and this says the manifest does.
 */
class ManifestGuardTest {

    @Test
    fun theOnlyActivityIsASingleInstance() {
        val manifest = File("src/androidMain/AndroidManifest.xml").readText()
        val activity = Regex("""<activity\b[^>]*android:name="\.MainActivity"[^>]*>""")
            .find(manifest)?.value.orEmpty()
        assertTrue(
            activity.contains("""android:launchMode="singleTask""""),
            "MainActivity must declare android:launchMode=\"singleTask\" — a second instance " +
                "is a second player and a second narrator over the same video:\n$activity",
        )
    }
}
