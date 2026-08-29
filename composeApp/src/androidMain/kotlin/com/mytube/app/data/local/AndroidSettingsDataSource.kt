package com.mytube.app.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The device's own key-value store.
 *
 * `SharedPreferences` rather than DataStore: this holds two short strings that
 * are read once at start-up and written when somebody edits a settings field.
 * DataStore's Flow-based API and its coroutine machinery are worth having for
 * state that changes while the app watches it, and that is not this.
 *
 * Reads go through `Dispatchers.IO` even though the first `getSharedPreferences`
 * is the only call that actually touches the disk — the rest is served from
 * memory. Doing it anyway costs nothing and keeps the promise the interface
 * makes, so a later move to a file or the keystore does not turn every call site
 * into a main-thread violation.
 */
class AndroidSettingsDataSource(context: Context) : SettingsDataSource {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("mytube", Context.MODE_PRIVATE)

    override suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        prefs.getString(key, null)
    }

    override suspend fun set(key: String, value: String) = withContext(Dispatchers.IO) {
        // commit(), not apply(). The caller is a suspend function that has been
        // told the write is done when it returns, and apply() would make that a
        // lie — the value is in memory but not yet on disk, which is exactly the
        // window in which somebody force-quits an app after changing a setting
        // and finds it did not stick.
        prefs.edit().putString(key, value).commit()
        Unit
    }
}
