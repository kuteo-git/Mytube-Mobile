package com.mytube.app.ui.watch

import com.mytube.app.ui.i18n.Strings

/**
 * What to say about a video YouTube will not hand over.
 *
 * The server answers with a token — `members_only`, `private`, `removed`,
 * `no_tier`, or whatever upstream said — and that token was being printed under
 * the title exactly as it arrived. Somebody who opens a members-only video read
 * "members_only", which tells them something is broken and nothing about what,
 * and offers no idea of what to do next. The web app has answered this properly
 * for a year; these are its four sentences.
 *
 * A pure function rather than a `when` inside the screen so it can be tested
 * without a device, and so the fallback is written down once. **Anything
 * unrecognised gets the generic sentence**, deliberately: a token this app has
 * not met yet still means "YouTube refused", and the alternative — showing the
 * raw word — is the fault this exists to fix, waiting for the next token the
 * server invents.
 */
fun unavailableCopy(reason: String, strings: Strings): String = when (reason) {
    // The gateway's own words, plus the aliases upstream uses for the same
    // three things. Matched loosely on purpose: `members_only`, `membersOnly`
    // and `members-only` are one answer, and picking only one spelling is how
    // this silently falls back to the generic line.
    "members_only", "membersOnly", "members-only" -> strings.unavailableMembersOnly
    "private" -> strings.unavailablePrivate
    "removed", "deleted" -> strings.unavailableRemoved
    else -> strings.unavailableGeneric
}
