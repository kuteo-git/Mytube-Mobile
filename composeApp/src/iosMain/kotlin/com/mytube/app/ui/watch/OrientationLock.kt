package com.mytube.app.ui.watch

/**
 * Which way up the app may be, read from Swift.
 *
 * ## Why this exists at all
 *
 * On iOS an app cannot simply rotate itself. The system asks the root view
 * controller — through the app delegate's
 * `application(_:supportedInterfaceOrientationsFor:)` — what it supports, and
 * rotates only within that answer. `ComposeUIViewController` is built by the
 * framework and cannot be subclassed from Kotlin to override it, so the answer
 * has to come from the Swift side.
 *
 * Rather than keep the flag in Swift and reach into it from Kotlin, it lives
 * here and Swift reads it: the decision belongs to the watch screen, and this
 * way the delegate is four lines that hold no state and cannot fall out of step.
 *
 * The plist lists portrait *and* both landscapes — it is the outer bound of what
 * is ever possible, and the delegate narrows it moment to moment. Listing only
 * portrait there would make this flag unable to do anything.
 */
object OrientationLock {

    /**
     * True only while the player is filling the screen.
     *
     * Not a `StateFlow`: the reader is UIKit asking a synchronous question on
     * the main thread, and it wants the value now rather than a subscription.
     */
    var landscapeAllowed: Boolean = false
        private set

    fun allow(landscape: Boolean) {
        landscapeAllowed = landscape
    }
}

/**
 * Called from `iOSApp.swift`.
 *
 * A top-level function rather than the object property directly, because this is
 * the one thing Swift needs and naming it as a function keeps the Kotlin object
 * an implementation detail on that side.
 */
fun landscapeAllowed(): Boolean = OrientationLock.landscapeAllowed
