package com.mytube.app.ui.shell

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * How a control the remote has landed on grows, and what outline its rim
 * follows.
 *
 * Sizes are in pixels at density 2, so the 16dp outset is 32px and the 64dp
 * tallest pill is 128px.
 */
class FocusShapeTest {

    private val density = 2f
    private val outset = 16f * density

    // --- focusScale ------------------------------------------------------------

    /** A shelf card is what a remote spends most of its time on: it lifts by the full ratio. */
    @Test
    fun `a card-sized control grows by the full focus scale`() {
        assertEquals(FOCUS_SCALE, focusScale(400f, 364f, outset))
        assertEquals(FOCUS_SCALE, focusScale(96f, 96f, outset))
    }

    /**
     * A ratio alone would carry a full-width row tens of dp past the screen's
     * edges. Capped, it grows by exactly the outset on each side.
     */
    @Test
    fun `a full-width row is capped by the outset`() {
        val scale = focusScale(1920f, 112f, outset)

        assertEquals((1920f + 2 * outset) / 1920f, scale, absoluteTolerance = 1e-6f)
        assertEquals(1920f + 2 * outset, 1920f * scale, absoluteTolerance = 1e-2f)
    }

    /** The smaller ratio wins, or a tall narrow control would stretch past its outset vertically. */
    @Test
    fun `a tall control is capped by its height`() {
        val scale = focusScale(200f, 2000f, outset)

        assertEquals((2000f + 2 * outset) / 2000f, scale, absoluteTolerance = 1e-6f)
    }

    @Test
    fun `nothing measured does not grow`() {
        assertEquals(1f, focusScale(0f, 0f, outset))
        assertEquals(1f, focusScale(0f, 100f, outset))
        assertEquals(1f, focusScale(100f, 0f, outset))
    }

    @Test
    fun `no outset allowed means no growth`() {
        assertEquals(1f, focusScale(400f, 364f, 0f))
    }

    // --- focusRimShape ---------------------------------------------------------

    /** Every small square pressable in this app is a glyph clipped to a circle. */
    @Test
    fun `a small square control is ringed as a circle`() {
        assertEquals(CircleShape, focusRimShape(96f, 96f, density))
        // Nearly square, within the range a glyph's padding drifts by.
        assertEquals(CircleShape, focusRimShape(100f, 90f, density))
    }

    /**
     * `CircleShape` is itself `RoundedCornerShape(50)`, so the two compare
     * equal — and on a square a pill *is* a circle. What this pins is the half
     * that differs: a short wide control has round ends, not a card's corners.
     */
    @Test
    fun `a short wide control is ringed as a pill`() {
        val shape = focusRimShape(400f, 96f, density)

        assertEquals(RoundedCornerShape(percent = 50), shape)
        assertNotEquals(RoundedCornerShape(12.dp), shape)
    }

    @Test
    fun `the tallest pill is the 64dp line, and one pixel more is a card`() {
        assertEquals(RoundedCornerShape(percent = 50), focusRimShape(400f, 128f, density))
        assertEquals(RoundedCornerShape(12.dp), focusRimShape(400f, 129f, density))
    }

    /**
     * Regression: a shelf card is nearly as tall as it is wide, and the first
     * build on the television ringed every one of them with a circle. A card's
     * corners are the thumbnail's, whatever its proportions.
     */
    @Test
    fun `a nearly square shelf card is not ringed as a circle`() {
        val shape = focusRimShape(400f, 364f, density)

        assertNotEquals(CircleShape, shape)
        assertEquals(RoundedCornerShape(12.dp), shape)
    }

    @Test
    fun `a large exact square is a card, not a circle`() {
        assertEquals(RoundedCornerShape(12.dp), focusRimShape(300f, 300f, density))
    }

    /** The same pixels at a lower density are a bigger control, and the line moves with it. */
    @Test
    fun `the pill line is in dp, not pixels`() {
        assertEquals(RoundedCornerShape(percent = 50), focusRimShape(400f, 96f, 2f))
        assertEquals(RoundedCornerShape(12.dp), focusRimShape(400f, 96f, 1f))
    }

    @Test
    fun `nothing measured has square corners rather than a crash`() {
        assertEquals(RoundedCornerShape(0.dp), focusRimShape(0f, 0f, density))
        assertEquals(RoundedCornerShape(0.dp), focusRimShape(0f, 96f, density))
        assertEquals(RoundedCornerShape(0.dp), focusRimShape(96f, 0f, density))
    }
}
