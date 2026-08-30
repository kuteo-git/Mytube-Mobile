package com.mytube.app.ui.watch

import com.mytube.app.ui.i18n.EnglishStrings
import com.mytube.app.ui.i18n.VietnameseStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The sentence shown over a video YouTube would not hand over.
 *
 * Worth a test because the fault it fixes was invisible in the code and obvious
 * on a phone: the wire token was printed straight through, so somebody opening a
 * members-only video read the word `members_only`. Nothing about that is wrong
 * as *code*, which is exactly why nothing caught it.
 */
class UnavailableCopyTest {

    @Test
    fun namesTheThreeReasonsTheServerSends() {
        val s = EnglishStrings
        assertEquals(s.unavailableMembersOnly, unavailableCopy("members_only", s))
        assertEquals(s.unavailablePrivate, unavailableCopy("private", s))
        assertEquals(s.unavailableRemoved, unavailableCopy("removed", s))
    }

    /**
     * The same answer whichever way upstream spells it.
     *
     * Matching only `members_only` would fall back to the generic sentence the
     * day the gateway passes YouTube's own spelling through, and the failure
     * would be a slightly vaguer paragraph — nothing anybody would report.
     */
    @Test
    fun acceptsTheOtherSpellingsOfTheSameReason() {
        val s = EnglishStrings
        assertEquals(s.unavailableMembersOnly, unavailableCopy("membersOnly", s))
        assertEquals(s.unavailableMembersOnly, unavailableCopy("members-only", s))
        assertEquals(s.unavailableRemoved, unavailableCopy("deleted", s))
    }

    /**
     * Anything unrecognised still reads as a sentence.
     *
     * `no_tier` is the gateway's own answer for a video whose formats cannot be
     * described, and it is the case that proves the rule: a token this app has
     * never met still means "YouTube refused", and the alternative is printing
     * the token — the fault being fixed, waiting for the next word the server
     * invents.
     */
    @Test
    fun anythingElseGetsASentenceRatherThanTheToken() {
        val s = EnglishStrings
        assertEquals(s.unavailableGeneric, unavailableCopy("no_tier", s))
        assertEquals(s.unavailableGeneric, unavailableCopy("", s))
        assertFalse(unavailableCopy("something_new", s).contains("something_new"))
    }

    @Test
    fun answersInWhicheverLanguageIsAsked() {
        assertEquals(
            VietnameseStrings.unavailablePrivate,
            unavailableCopy("private", VietnameseStrings),
        )
    }
}
