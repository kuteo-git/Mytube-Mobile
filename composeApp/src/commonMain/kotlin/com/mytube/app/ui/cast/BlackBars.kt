package com.mytube.app.ui.cast

import coil3.transform.Transformation

/** A rectangle of pixels, right and bottom exclusive. */
data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Where the picture is in a cover with black bars baked in.
 *
 * A song's thumbnail is often the video's frame, and a song uploaded as a
 * still is a square or 4:3 picture between black pillars in a 16:9 frame —
 * five of six on the speaker's queue, measured. Drawn as it is, the cover was
 * square one row and wide the next. Cropped to this, `ContentScale.Crop` fills
 * the frame from the picture's centre.
 *
 * A bar is a run of columns (or rows) from an edge that are black but for
 * JPEG's noise, the same width on both sides; anything else is the picture —
 * a night scene on one side is not a pillarbox.
 */
fun contentBounds(width: Int, height: Int, luma: (x: Int, y: Int) -> Int): Bounds {
    val whole = Bounds(0, 0, width, height)
    if (width <= 0 || height <= 0) return whole
    val rowStep = maxOf(1, height / SAMPLES)
    val columnStep = maxOf(1, width / SAMPLES)
    fun darkColumn(x: Int): Boolean = dark(0 until height step rowStep) { luma(x, it) }
    fun darkRow(y: Int): Boolean = dark(0 until width step columnStep) { luma(it, y) }

    val left = (0 until width).firstOrNull { !darkColumn(it) } ?: return whole
    val right = (width - 1 downTo 0).first { !darkColumn(it) } + 1
    val top = (0 until height).firstOrNull { !darkRow(it) } ?: return whole
    val bottom = (height - 1 downTo 0).first { !darkRow(it) } + 1

    val (x0, x1) = if (bars(left, width - right, width)) left to right else 0 to width
    val (y0, y1) = if (bars(top, height - bottom, height)) top to bottom else 0 to height
    return Bounds(x0, y0, x1, y1)
}

/** Black but for noise: gives up at the first lit sample past the noise budget. */
private inline fun dark(at: IntProgression, luma: (Int) -> Int): Boolean {
    val budget = (at.count() * NOISE_SHARE).toInt()
    var lit = 0
    for (i in at) if (luma(i) > DARK_LUMA && ++lit > budget) return false
    return true
}

/** Two bars, one on each side, alike and wide enough to be bars. */
private fun bars(start: Int, end: Int, size: Int): Boolean {
    val least = size * MIN_BAR_SHARE
    return start >= least && end >= least && kotlin.math.abs(start - end) <= size * BAR_SLACK
}

/** Rec. 601 luma of an ARGB pixel, 0..255. */
fun lumaOf(argb: Int): Int {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return (299 * r + 587 * g + 114 * b) / 1000
}

/** Black, as ffmpeg's cropdetect means it. */
private const val DARK_LUMA = 24
private const val NOISE_SHARE = 0.02
private const val MIN_BAR_SHARE = 0.03
private const val BAR_SLACK = 0.03
private const val SAMPLES = 180

/** Crops a cover to [contentBounds]; see there. */
expect fun trimBlackBars(): Transformation

internal const val TRIM_BLACK_BARS_KEY = "trim-black-bars-1"
