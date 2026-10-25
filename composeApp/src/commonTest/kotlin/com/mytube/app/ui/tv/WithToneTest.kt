package com.mytube.app.ui.tv

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The on-screen keyboard's tone keys put the mark where Vietnamese spelling
 * puts it, on the last word typed.
 *
 * The expected words are ordinary Vietnamese spelling, not derived from the
 * code: each is a word somebody would search for.
 */
class WithToneTest {

    @Test
    fun `a single vowel takes the tone`() {
        assertEquals("má", withTone("ma", Tone.Acute))
        assertEquals("mà", withTone("ma", Tone.Grave))
        assertEquals("mả", withTone("ma", Tone.Hook))
        assertEquals("mã", withTone("ma", Tone.Tilde))
        assertEquals("mạ", withTone("ma", Tone.Dot))
    }

    @Test
    fun `only the last word is marked`() {
        assertEquals("son tùng", withTone("son tung", Tone.Grave))
    }

    @Test
    fun `a new tone replaces the one already there`() {
        assertEquals("mà", withTone("má", Tone.Grave))
    }

    @Test
    fun `a word with no vowel is left alone`() {
        assertEquals("ng", withTone("ng", Tone.Acute))
        assertEquals("", withTone("", Tone.Acute))
    }

    /** The vowel with its own diacritic carries the tone — and in ươ, the ơ. */
    @Test
    fun `a vowel with a hat or a horn takes the tone`() {
        assertEquals("tiến", withTone("tiên", Tone.Acute))
        assertEquals("người", withTone("ngươi", Tone.Grave))
        assertEquals("cuối", withTone("cuôi", Tone.Acute))
    }

    /** Two vowels ending the word: the first, as most typing tools write it. */
    @Test
    fun `a cluster ending the word marks its first vowel`() {
        assertEquals("múa", withTone("mua", Tone.Acute))
        assertEquals("hóa", withTone("hoa", Tone.Acute))
        assertEquals("tủi", withTone("tui", Tone.Hook))
    }

    /** Closed by a consonant, the cluster's last vowel takes it. */
    @Test
    fun `a cluster closed by a consonant marks its last vowel`() {
        assertEquals("hoàn", withTone("hoan", Tone.Grave))
        assertEquals("toán", withTone("toan", Tone.Acute))
    }

    /** The u of qu and the i of gi belong to the consonant, not the vowel. */
    @Test
    fun `qu and gi are consonants`() {
        assertEquals("quả", withTone("qua", Tone.Hook))
        assertEquals("già", withTone("gia", Tone.Grave))
        assertEquals("quyết", withTone("quyêt", Tone.Acute))
        assertEquals("gì", withTone("gi", Tone.Grave))
    }
}
