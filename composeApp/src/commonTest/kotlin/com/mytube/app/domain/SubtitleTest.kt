package com.mytube.app.domain

import com.mytube.app.domain.model.SubtitleCue
import com.mytube.app.domain.model.cueAt
import com.mytube.app.domain.model.parseWebVtt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubtitleTest {

    @Test
    fun `parses the shape YouTube actually writes`() {
        val cues = parseWebVtt(
            """
            WEBVTT
            Kind: captions
            Language: en

            00:00:01.120 --> 00:00:03.480
            Hello and welcome back

            00:00:03.480 --> 00:00:06.000
            to the show.
            """.trimIndent(),
        )

        assertEquals(2, cues.size)
        assertEquals(1.12, cues[0].startSeconds)
        assertEquals(3.48, cues[0].endSeconds)
        assertEquals("Hello and welcome back", cues[0].text)
        assertEquals("to the show.", cues[1].text)
    }

    /**
     * The hours field is optional and YouTube omits it under an hour. A parser
     * that requires three parts drops every cue of most videos in this library.
     */
    @Test
    fun `accepts timestamps with no hours field`() {
        val cues = parseWebVtt("WEBVTT\n\n01:02.500 --> 01:04.000\nshort form\n")
        assertEquals(1, cues.size)
        assertEquals(62.5, cues[0].startSeconds)
        assertEquals(64.0, cues[0].endSeconds)
    }

    /** Fetched over HTTP a file may carry CRLF; a trailing \r breaks every end time. */
    @Test
    fun `survives windows line endings`() {
        val cues = parseWebVtt("WEBVTT\r\n\r\n00:00.000 --> 00:02.000\r\ncarriage\r\n")
        assertEquals(1, cues.size)
        assertEquals(2.0, cues[0].endSeconds)
        assertEquals("carriage", cues[0].text)
    }

    /** Cue settings sit after the end time on the same line. */
    @Test
    fun `ignores cue settings after the end time`() {
        val cues = parseWebVtt("WEBVTT\n\n00:00.000 --> 00:02.000 align:start position:10%\nsettings\n")
        assertEquals(1, cues.size)
        assertEquals(2.0, cues[0].endSeconds)
        assertEquals("settings", cues[0].text)
    }

    @Test
    fun `strips markup and keeps the words`() {
        val cues = parseWebVtt("WEBVTT\n\n00:00.000 --> 00:02.000\n<v Alice>hello <c.loud>there</c>\n")
        assertEquals("hello there", cues[0].text)
    }

    @Test
    fun `joins a cue written over several lines`() {
        val cues = parseWebVtt("WEBVTT\n\n00:00.000 --> 00:02.000\nfirst\nsecond\n")
        assertEquals("first\nsecond", cues[0].text)
    }

    /**
     * The server once wrote XML into a file named `.vtt` and every layer below
     * reported success — the catalogue listed the track, `/media` served it 200,
     * and the player showed nothing. A body that does not say WEBVTT is refused.
     */
    @Test
    fun `refuses a file that is not webvtt`() {
        assertTrue(parseWebVtt("<?xml version=\"1.0\"?><timedtext format=\"3\">").isEmpty())
    }

    /**
     * The shape YouTube's auto-generated files actually have: a line holding a
     * single space under the timestamp, inline word timings in the text, and
     * every line published twice — once over its real span and again as a 10ms
     * cue with the final wording.
     *
     * The space is why this mattered. WebVTT ends a cue at an *empty* line, and
     * ending it at a *blank* one cut every cue off before its first word: the
     * file parsed, served and looked perfect, and produced no captions at all.
     */
    @Test
    fun `reads a youtube auto-generated file`() {
        val cues = parseWebVtt(
            "WEBVTT\nKind: captions\nLanguage: en\n\n" +
                "00:00:02.639 --> 00:00:06.540\n" +
                " \n" +
                "Hi,<00:00:03.040><c> I'm</c><00:00:03.200><c> Matthew.</c>\n\n" +
                "00:00:06.540 --> 00:00:06.550\n" +
                "Hi, I'm Matthew.\n \n",
        )

        assertEquals(1, cues.size)
        assertEquals("Hi, I'm Matthew.", cues[0].text)
        assertEquals(2.639, cues[0].startSeconds)
        assertEquals(6.540, cues[0].endSeconds)
    }

    @Test
    fun `drops a cue with no words`() {
        val cues = parseWebVtt("WEBVTT\n\n00:00.000 --> 00:02.000\n\n00:02.000 --> 00:04.000\nreal\n")
        assertEquals(1, cues.size)
        assertEquals("real", cues[0].text)
    }

    @Test
    fun `finds the cue on screen, and none between cues`() {
        val cues = listOf(
            SubtitleCue(1.0, 2.0, "first"),
            SubtitleCue(3.0, 4.0, "second"),
        )
        assertNull(cueAt(cues, 0.5))
        assertEquals("first", cueAt(cues, 1.0)?.text)
        assertEquals("first", cueAt(cues, 1.99)?.text)
        // The end is exclusive, so two cues that touch never both match.
        assertNull(cueAt(cues, 2.0))
        assertEquals("second", cueAt(cues, 3.5)?.text)
        assertNull(cueAt(cues, 9.0))
    }

    /**
     * A caption file is XML underneath, so its text arrives escaped.
     *
     * Found on the simulator rather than reasoned about: a CBC clip drew
     * `&gt;&gt; That's BC MP Zoe Royer` across the picture. Nothing in the code
     * looked wrong — the parser was faithfully showing what the file said.
     */
    @Test
    fun `decodes the html escapes a caption file is written with`() {
        val cues = parseWebVtt(
            """
            WEBVTT

            00:00:01.000 --> 00:00:03.000
            &gt;&gt; That&#39;s BC MP Zoe Royer &amp; friends
            """.trimIndent(),
        )
        assertEquals(1, cues.size)
        assertEquals(">> That's BC MP Zoe Royer & friends", cues[0].text)
    }

    /**
     * An escaped escape stays escaped.
     *
     * `&amp;gt;` is a caption quoting the characters `&gt;`. Decoding `&amp;`
     * before `&gt;` would turn it into `>` — the file's own words rewritten to
     * mean something else. This is why the order in `decodeEntities` matters.
     */
    @Test
    fun `does not decode an escape twice`() {
        val cues = parseWebVtt(
            """
            WEBVTT

            00:00:01.000 --> 00:00:03.000
            write &amp;gt; for a chevron
            """.trimIndent(),
        )
        assertEquals("write &gt; for a chevron", cues[0].text)
    }
}
