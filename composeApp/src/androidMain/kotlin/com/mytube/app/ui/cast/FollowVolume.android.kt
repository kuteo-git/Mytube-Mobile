package com.mytube.app.ui.cast

import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.mytube.app.ui.watch.findActivity
import kotlin.math.roundToInt

/**
 * The media stream's volume.
 *
 * The activity's volume keys are pointed at the media stream while this is
 * held: with nothing playing here they would otherwise move the ringer, and the
 * speaker would never hear of it.
 */
@Composable
internal actual fun rememberPhoneVolume(): PhoneVolume? {
    val context = LocalContext.current
    val volume = remember { MediaVolume(context.getSystemService(Context.AUDIO_SERVICE) as AudioManager) }
    val activity = context.findActivity()
    DisposableEffect(volume, activity) {
        val stream = activity?.volumeControlStream
        activity?.volumeControlStream = AudioManager.STREAM_MUSIC
        onDispose {
            if (stream != null) activity.volumeControlStream = stream
            volume.restore()
        }
    }
    return volume
}

private class MediaVolume(private val audio: AudioManager) : PhoneVolume {
    private val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    private val original = audio.getStreamVolume(AudioManager.STREAM_MUSIC)

    override fun read(): Int = (audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max).roundToInt()

    // No flags: the level follows the speaker silently. A press shows the
    // system's own panel, which is the feedback a press wants.
    override fun set(percent: Int) =
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (percent * max / 100f).roundToInt(), 0)

    fun restore() = audio.setStreamVolume(AudioManager.STREAM_MUSIC, original, 0)
}
