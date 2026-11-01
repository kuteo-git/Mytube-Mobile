package com.mytube.app.ui.cast

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.outputVolume
import platform.CoreGraphics.CGRectMake
import platform.MediaPlayer.MPVolumeView
import platform.UIKit.UIApplication
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UISlider
import platform.UIKit.UIView
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import kotlin.math.roundToInt

/**
 * The system volume.
 *
 * Read from the audio session; set through an `MPVolumeView`'s slider, which
 * is the only way an app may move it. The view is in the window and out of
 * sight — in the window is also what keeps the system's volume overlay from
 * covering the screen each time the speaker's level is put on the phone.
 *
 * The audio session is the player's, which exists for as long as the watch
 * screen follows the speaker; without an active session the buttons would move
 * the ringer and this would read nothing.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun rememberPhoneVolume(): PhoneVolume? {
    val volume = remember { SystemVolume() }
    DisposableEffect(volume) { onDispose { volume.release() } }
    return volume
}

@OptIn(ExperimentalForeignApi::class)
private class SystemVolume : PhoneVolume {
    private val original = read()
    private val view = MPVolumeView(frame = CGRectMake(-2_000.0, -2_000.0, 1.0, 1.0)).also { view ->
        UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .flatMap { it.windows.filterIsInstance<UIWindow>() }
            .firstOrNull { it.isKeyWindow() }
            ?.addSubview(view)
    }

    override fun read(): Int = (AVAudioSession.sharedInstance().outputVolume * 100).roundToInt()

    override fun set(percent: Int) {
        // The simulator has no system volume: its view is a "No volume
        // available" label and a route button, and the phone does not follow.
        val slider = slider(view) ?: return
        slider.setValue(percent / 100f, animated = false)
        slider.sendActionsForControlEvents(UIControlEventValueChanged)
    }

    fun release() {
        set(original)
        view.removeFromSuperview()
    }

    // Searched for rather than taken from the top level: the view's insides are
    // Apple's and not promised.
    private fun slider(v: UIView): UISlider? {
        if (v is UISlider) return v
        return v.subviews.filterIsInstance<UIView>().firstNotNullOfOrNull { slider(it) }
    }
}
