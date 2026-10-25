package com.mytube.app.ui.tv

/**
 * The five Vietnamese tone marks, as the on-screen keyboard offers them.
 *
 * A remote cannot type Telex at speed, and an on-screen keyboard with a key for
 * every toned vowel would be seventy keys. So the keyboard has the letters, the
 * seven vowels with their own diacritic (ă â đ ê ô ơ ư are letters, not
 * accents), and these five keys that mark the word already typed.
 */
enum class Tone { Acute, Grave, Hook, Tilde, Dot }

/**
 * [text] with [tone] on the last word, where Vietnamese spelling puts it.
 *
 * Any tone the word already carries is replaced, so pressing the wrong key and
 * then the right one is a correction rather than a pile of marks. A word with
 * no vowel is returned as it was.
 *
 * A named function with a test because where a tone goes is a rule with
 * exceptions — *qu* and *gi*, *ươ*, a cluster closed by a consonant or not —
 * and getting one wrong searches the library for a word nobody wrote.
 */
internal fun withTone(text: String, tone: Tone): String {
    val start = text.lastIndexOf(' ') + 1
    val word = text.substring(start).map(::untoned).joinToString("")
    val at = toneIndex(word)
    if (at < 0) return text
    val marked = word.substring(0, at) + toned(word[at], tone) + word.substring(at + 1)
    return text.substring(0, start) + marked
}

/**
 * Which letter of an untoned [word] takes the tone, or −1 for none.
 *
 * The last run of vowels is the syllable's nucleus. Then, in order: the u of
 * *qu* and the i of *gi* are the consonant's, not the nucleus's; a vowel with
 * its own diacritic takes the mark (the last one, so *ươ* marks the ơ); a lone
 * vowel takes it; a run closed by a consonant marks its last vowel (hoàn); an
 * open run of three marks its middle (ngoài); an open pair marks its first
 * (múa, hóa) — the older placement most typing tools still write by default.
 */
private fun toneIndex(word: String): Int {
    val end = word.indexOfLast { it in VOWELS }
    if (end < 0) return -1
    var start = end
    while (start > 0 && word[start - 1] in VOWELS) start--
    if (end > start) {
        val before = if (start > 0) word[start - 1] else ' '
        val first = word[start]
        if ((before == 'q' && first == 'u') || (before == 'g' && first == 'i')) start++
    }
    val hatted = (start..end).lastOrNull { word[it] in HATTED }
    return when {
        hatted != null -> hatted
        start == end -> start
        end < word.lastIndex -> end
        end - start >= 2 -> start + 1
        else -> start
    }
}

/** The vowels that carry a diacritic of their own, and so carry the tone. */
private val HATTED = setOf('ă', 'â', 'ê', 'ô', 'ơ', 'ư')

/**
 * Each vowel in its six forms: untoned, then sắc, huyền, hỏi, ngã, nặng — the
 * order of [Tone] after the bare letter.
 */
private val FORMS = listOf(
    "aáàảãạ", "ăắằẳẵặ", "âấầẩẫậ", "eéèẻẽẹ", "êếềểễệ", "iíìỉĩị",
    "oóòỏõọ", "ôốồổỗộ", "ơớờởỡợ", "uúùủũụ", "ưứừửữự", "yýỳỷỹỵ",
)

private val VOWELS: Set<Char> = FORMS.map { it[0] }.toSet()

private fun untoned(c: Char): Char = FORMS.firstOrNull { c in it }?.get(0) ?: c

private fun toned(c: Char, tone: Tone): Char =
    FORMS.first { it[0] == c }[tone.ordinal + 1]
