package com.mytube.app.ui.cast

import coil3.Bitmap
import coil3.size.Size
import coil3.transform.Transformation
import org.jetbrains.skia.IRect

actual fun trimBlackBars(): Transformation = object : Transformation() {
    override val cacheKey = TRIM_BLACK_BARS_KEY

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val box = contentBounds(input.width, input.height) { x, y -> lumaOf(input.getColor(x, y)) }
        if (box.width == input.width && box.height == input.height) return input
        val cropped = Bitmap()
        return if (input.extractSubset(cropped, IRect.makeLTRB(box.left, box.top, box.right, box.bottom))) cropped else input
    }
}
