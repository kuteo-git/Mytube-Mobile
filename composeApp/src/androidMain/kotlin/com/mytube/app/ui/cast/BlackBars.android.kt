package com.mytube.app.ui.cast

import coil3.Bitmap
import coil3.size.Size
import coil3.transform.Transformation

actual fun trimBlackBars(): Transformation = object : Transformation() {
    override val cacheKey = TRIM_BLACK_BARS_KEY

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = input.width
        val h = input.height
        val pixels = IntArray(w * h)
        input.getPixels(pixels, 0, w, 0, 0, w, h)
        val box = contentBounds(w, h) { x, y -> lumaOf(pixels[y * w + x]) }
        if (box.width == w && box.height == h) return input
        return android.graphics.Bitmap.createBitmap(input, box.left, box.top, box.width, box.height)
    }
}
