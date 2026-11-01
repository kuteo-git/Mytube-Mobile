package com.mytube.app.ui.cast

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.TimeSource

/**
 * The phone's own volume, as the platform holds it.
 *
 * Set and read in percent, in the platform's own steps. Whoever made it puts
 * the phone back where it was when it is let go.
 */
internal interface PhoneVolume {
    fun read(): Int
    fun set(percent: Int)
}

/** The phone's volume while this is composed, or null where there is none to hold. */
@Composable
internal expect fun rememberPhoneVolume(): PhoneVolume?

/**
 * While a speaker plays, the phone's volume buttons are the speaker's.
 *
 * The phone is put at the speaker's level and kept there as the speaker
 * changes, and a press on the phone is sent to the speaker — one number, two
 * places, as a Cast app does. Composed only while the app is on screen, which
 * is also the only time the speaker is being asked about; the phone's own level
 * comes back when it is let go. [percent] null is nothing playing.
 */
@Composable
fun FollowVolume(percent: Int?, onPress: (Int) -> Unit) {
    if (percent == null) return
    val phone = rememberPhoneVolume() ?: return
    val follower = remember(phone) { VolumeFollower() }
    val start = remember { TimeSource.Monotonic.markNow() }
    val press by rememberUpdatedState(onPress)
    LaunchedEffect(phone, percent) {
        follower.speaker(percent, start.elapsedNow().inWholeMilliseconds)?.let(phone::set)
    }
    LaunchedEffect(phone) {
        // Read rather than observed: a fifth of a second is quicker than a
        // finger presses twice, and neither platform reports a press it was
        // not the audio focus for anyway.
        while (isActive) {
            follower.phone(phone.read(), start.elapsedNow().inWholeMilliseconds)?.let { press(it) }
            delay(VOLUME_READ_MILLIS)
        }
    }
}

private const val VOLUME_READ_MILLIS = 200L
