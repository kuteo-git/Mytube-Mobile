package com.mytube.app

import android.content.Context
import com.mytube.app.data.local.AndroidSettingsDataSource

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
object MytubeApp {
    private var instance: AppContainer? = null

    fun container(context: Context): AppContainer = synchronized(this) {
        instance ?: AppContainer(AndroidSettingsDataSource(context)).also { instance = it }
    }
}
