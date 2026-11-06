package com.mytube.app.ui.shell

import androidx.compose.runtime.compositionLocalOf

/** What this device draws behind and over its panes. */
enum class Effects {
    /** Sampled glass, platform glass, and the picture's reflection. */
    On,

    /** The same device, switched off in Settings: every material falls back to paint. */
    Off,

    /** The device cannot blur, so the switch shows off and cannot be pressed. */
    Unavailable,
}

val Effects.on: Boolean get() = this == Effects.On

/**
 * The one rule for the effects switch.
 *
 * [supported] is whether the platform can blur; [wanted] is the stored
 * preference. A television never samples, so it is unavailable whatever was
 * stored.
 */
fun effectsOf(supported: Boolean, television: Boolean, wanted: Boolean): Effects = when {
    !supported || television -> Effects.Unavailable
    wanted -> Effects.On
    else -> Effects.Off
}

/**
 * Whether the platform can blur: iOS, and Android 12 and later, where
 * `RenderEffect` exists. Below 12 the library drew sampled glass without the
 * blur, which reads worse than paint.
 */
expect val effectsSupported: Boolean

/** Provided once in `App`; unavailable until then, so nothing samples by accident. */
val LocalEffects = compositionLocalOf { Effects.Unavailable }
