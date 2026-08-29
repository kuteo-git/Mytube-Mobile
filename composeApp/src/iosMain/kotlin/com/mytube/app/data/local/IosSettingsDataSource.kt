package com.mytube.app.data.local

import platform.Foundation.NSUserDefaults

/**
 * The device's own key-value store, iOS side.
 *
 * `NSUserDefaults` is the direct counterpart of SharedPreferences and needs no
 * library. It is backed by an in-memory cache that the system flushes, so there
 * is nothing to await and no dispatcher to hop to — the suspend modifier here is
 * the interface's promise rather than this implementation's need.
 *
 * `synchronize()` is deliberately not called. Apple deprecated it and documents
 * that it is unnecessary; calling it would be a superstition carried over from
 * the Android side's `commit()`, which has a real reason there.
 */
class IosSettingsDataSource : SettingsDataSource {

    private val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun get(key: String): String? = defaults.stringForKey(key)

    override suspend fun set(key: String, value: String) =
        defaults.setObject(value, forKey = key)
}
