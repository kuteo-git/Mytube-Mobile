package com.mytube.app.ui.cast

import kotlin.math.abs

/**
 * The phone's volume and a speaker's, kept as one number while it plays.
 *
 * Each side hears the other's write come back. The phone reads back what was
 * just set on it — and in its own steps, so 27 comes back as 25 — and the
 * speaker is polled, so its report can leave before a press and arrive after
 * it. Without these rules the two argue, and a press is undone by the poll
 * that was already on its way.
 *
 * - A speaker value is put on the phone when it is new, not on every report.
 * - Readings after that are the phone landing, not a press, until one is near
 *   the value set or [landMillis] has passed: iOS moves a moment after being
 *   asked, and a reading taken before it moved is the old level.
 * - A press is sent, and for [holdMillis] reports other than the value sent
 *   are older than the press and are ignored.
 */
class VolumeFollower(
    private val holdMillis: Long = 2_000,
    private val landMillis: Long = 600,
) {
    private var phoneAt: Int? = null
    private var landing: Int? = null
    private var landBy = 0L
    private var heard: Int? = null
    private var sent: Int? = null
    private var holdUntil = 0L

    /** The speaker reported [percent]; returns what to set the phone to, or null. */
    fun speaker(percent: Int, now: Long): Int? {
        val pressed = sent
        if (pressed != null) {
            if (percent == pressed) {
                sent = null
                heard = percent
                return null
            }
            if (now < holdUntil) return null
            // The press was never taken: the phone shows a level the speaker
            // is not at, so it is put back even if this value was heard before.
            sent = null
            heard = percent
            land(percent, now)
            return percent
        }
        if (percent == heard) return null
        heard = percent
        land(percent, now)
        return percent
    }

    private fun land(percent: Int, now: Long) {
        landing = percent
        landBy = now + landMillis
    }

    /** The phone reads [percent]; returns what to send the speaker, or null. */
    fun phone(percent: Int, now: Long): Int? {
        val target = landing
        if (target != null) {
            phoneAt = percent
            if (abs(percent - target) <= LANDING_SLACK || now >= landBy) landing = null
            return null
        }
        if (percent == phoneAt) return null
        phoneAt = percent
        // Before the speaker has been heard there is nothing to follow yet.
        if (heard == null && sent == null) return null
        sent = percent
        holdUntil = now + holdMillis
        return percent
    }
}

/** How far from the value set the phone may land: one of iOS's sixteen steps. */
private const val LANDING_SLACK = 7
