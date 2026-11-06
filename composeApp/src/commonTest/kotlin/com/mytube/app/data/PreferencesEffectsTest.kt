package com.mytube.app.data

import com.mytube.app.data.local.SettingsDataSource
import com.mytube.app.data.repository.PreferencesRepositoryImpl
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreferencesEffectsTest {

    private class MemorySettings : SettingsDataSource {
        val values = mutableMapOf<String, String>()
        override suspend fun get(key: String): String? = values[key]
        override suspend fun set(key: String, value: String) {
            values[key] = value
        }
    }

    @Test
    fun aFreshDeviceDrawsEffects() = runTest {
        assertTrue(PreferencesRepositoryImpl(MemorySettings()).effects())
    }

    @Test
    fun switchedOffStaysOffAndBackOnIsOn() = runTest {
        val prefs = PreferencesRepositoryImpl(MemorySettings())
        prefs.setEffects(false)
        assertFalse(prefs.effects())
        prefs.setEffects(true)
        assertTrue(prefs.effects())
    }
}
