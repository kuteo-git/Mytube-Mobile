package com.mytube.app.ui.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EffectsTest {

    @Test
    fun aDeviceThatCanBlurAndIsAskedToDoesSo() {
        assertEquals(Effects.On, effectsOf(supported = true, television = false, wanted = true))
    }

    @Test
    fun aDeviceThatCanBlurButIsAskedNotToDoesNot() {
        assertEquals(Effects.Off, effectsOf(supported = true, television = false, wanted = false))
    }

    @Test
    fun aDeviceThatCannotBlurIsUnavailableWhateverWasStored() {
        assertEquals(Effects.Unavailable, effectsOf(supported = false, television = false, wanted = true))
        assertEquals(Effects.Unavailable, effectsOf(supported = false, television = false, wanted = false))
    }

    @Test
    fun aTelevisionIsUnavailableWhateverWasStored() {
        assertEquals(Effects.Unavailable, effectsOf(supported = true, television = true, wanted = true))
        assertEquals(Effects.Unavailable, effectsOf(supported = true, television = true, wanted = false))
    }

    @Test
    fun onlyOnDrawsEffects() {
        assertTrue(Effects.On.on)
        assertFalse(Effects.Off.on)
        assertFalse(Effects.Unavailable.on)
    }
}
