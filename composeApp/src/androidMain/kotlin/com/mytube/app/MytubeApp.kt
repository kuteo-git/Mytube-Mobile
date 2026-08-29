package com.mytube.app

import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.mytube.app.data.local.AndroidSettingsDataSource
import com.mytube.app.player.ExoVideoPlayerFactory

/**
 * The process-wide container, built on first use.
 *
 * Not an `Application` subclass: that would mean a manifest entry and a class
 * that exists only to hold one field. `applicationContext` is what
 * SharedPreferences needs and it outlives every Activity, so holding a container
 * built from it leaks nothing.
 *
 * `synchronized` rather than `by lazy` on a top-level val, because the first
 * caller is an Activity and Android may create two of them in quick succession
 * during a configuration change.
 */
@UnstableApi
object MytubeApp {
    private var instance: AppContainer? = null

    fun container(context: Context): AppContainer = synchronized(this) {
        instance ?: AppContainer(
            settings = AndroidSettingsDataSource(context),
            playerFactory = ExoVideoPlayerFactory(context),
        ).also { instance = it }
    }
}
