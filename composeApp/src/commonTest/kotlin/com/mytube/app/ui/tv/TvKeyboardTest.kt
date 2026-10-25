package com.mytube.app.ui.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The television's on-screen keyboard: what each key does to the query, and
 * which keys each layout shows.
 *
 * The expected rows are written out as the keyboard is read on screen, not
 * built the way the code builds them.
 */
class TvKeyboardTest {

    // --- what a key does to the text ------------------------------------------

    @Test
    fun `a letter is appended lower-case`() {
        assertEquals("a", typed("", TvKey.Letter('A')))
        assertEquals("son", typed("so", TvKey.Letter('N')))
        assertEquals("abc", typed("ab", TvKey.Letter('c')))
    }

    /** The Vietnamese letters have their own lower case, and it is not the Latin one. */
    @Test
    fun `a Vietnamese letter is appended lower-case`() {
        assertEquals("đ", typed("", TvKey.Letter('Đ')))
        assertEquals("ă", typed("", TvKey.Letter('Ă')))
        assertEquals("ơ", typed("", TvKey.Letter('Ơ')))
    }

    @Test
    fun `a word typed key by key then marked takes the tone`() {
        val keys = listOf(TvKey.Letter('M'), TvKey.Letter('A'), TvKey.Mark(Tone.Acute))

        assertEquals("má", typeAll(keys))
    }

    @Test
    fun `a tone mark goes on the last word only`() {
        assertEquals("son tùng", typed("son tung", TvKey.Mark(Tone.Grave)))
    }

    @Test
    fun `backspace drops the last character`() {
        assertEquals("so", typed("son", TvKey.Backspace))
        assertEquals("son", typed("son ", TvKey.Backspace))
    }

    @Test
    fun `backspace on an empty query leaves it empty`() {
        assertEquals("", typed("", TvKey.Backspace))
    }

    @Test
    fun `space separates words`() {
        assertEquals("son ", typed("son", TvKey.Space))
    }

    /** A stray press on space before anything is typed must not change what is asked. */
    @Test
    fun `space never leads`() {
        assertEquals("", typed("", TvKey.Space))
    }

    @Test
    fun `space never doubles`() {
        assertEquals("son ", typed("son ", TvKey.Space))
        val keys = listOf(TvKey.Letter('a'), TvKey.Space, TvKey.Space, TvKey.Space, TvKey.Letter('b'))
        assertEquals("a b", typeAll(keys))
    }

    @Test
    fun `clear empties the query`() {
        assertEquals("", typed("son tùng", TvKey.Clear))
        assertEquals("", typed("", TvKey.Clear))
    }

    /** These keys are about the keyboard, not the text. */
    @Test
    fun `search, symbols and language leave the text unchanged`() {
        for (key in listOf(TvKey.Search, TvKey.Symbols, TvKey.Language)) {
            assertEquals("son tùng ", typed("son tùng ", key), "$key")
            assertEquals("", typed("", key), "$key")
        }
    }

    // --- which keys each layout shows ------------------------------------------

    /** Seven across in alphabetical order, as YouTube's TV app lays its own out. */
    @Test
    fun `the Latin layout is the alphabet in four rows of seven`() {
        assertEquals(
            listOf("abcdefg", "hijklmn", "opqrstu", "vwxyz-'"),
            spelled(keyboardRows(KeyboardLayout.Latin)),
        )
    }

    @Test
    fun `the Vietnamese layout is the Latin rows, then the diacritic letters, then the tones`() {
        val rows = keyboardRows(KeyboardLayout.Vietnamese)

        assertEquals(6, rows.size)
        assertEquals(keyboardRows(KeyboardLayout.Latin), rows.take(4))
        assertEquals(listOf("ăâđêôơư"), spelled(listOf(rows[4])))
        // sắc, huyền, hỏi, ngã, nặng: the order the tones are taught in.
        assertEquals(
            listOf(Tone.Acute, Tone.Grave, Tone.Hook, Tone.Tilde, Tone.Dot).map { TvKey.Mark(it) },
            rows[5],
        )
    }

    @Test
    fun `the symbols layout has every digit`() {
        val chars = keyboardRows(KeyboardLayout.Symbols).flatten()
            .filterIsInstance<TvKey.Letter>()
            .map { it.char }
            .toSet()

        for (digit in "0123456789") assertTrue(digit in chars, "symbols layout lacks $digit")
    }

    /** A row wider than seven would run under the side keys. */
    @Test
    fun `no row in any layout is wider than seven keys`() {
        for (layout in KeyboardLayout.entries) {
            keyboardRows(layout).forEachIndexed { index, row ->
                assertTrue(row.size <= 7, "$layout row $index has ${row.size} keys")
            }
        }
    }

    // --- the language key -------------------------------------------------------

    @Test
    fun `the language key switches between Latin and Vietnamese`() {
        assertEquals(KeyboardLayout.Vietnamese, otherLanguage(KeyboardLayout.Latin))
        assertEquals(KeyboardLayout.Latin, otherLanguage(KeyboardLayout.Vietnamese))
    }

    /** From the digits, the language key leads to letters — the Vietnamese ones. */
    @Test
    fun `the language key on the symbols layout leads to Vietnamese`() {
        assertEquals(KeyboardLayout.Vietnamese, otherLanguage(KeyboardLayout.Symbols))
    }

    private fun typeAll(keys: List<TvKey>): String = keys.fold("") { text, key -> typed(text, key) }

    /** A row of letter keys read back as the characters on them. */
    private fun spelled(rows: List<List<TvKey>>): List<String> =
        rows.map { row -> row.joinToString("") { (it as TvKey.Letter).char.toString() } }
}
