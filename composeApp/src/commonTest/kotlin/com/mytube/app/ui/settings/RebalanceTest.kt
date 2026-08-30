package com.mytube.app.ui.settings

import com.mytube.app.domain.repository.FeedMix
import kotlin.test.Test
import kotlin.test.assertEquals

class RebalanceTest {

    private fun mix(s: Int, a: Int, d: Int) = FeedMix(s, a, d, fixedPercent = 28)

    private fun FeedMix.total() = subscribedPercent + affinityPercent + discoveryPercent

    @Test
    fun theThreeAlwaysTotalAHundred() {
        // The server takes three numbers that must total 100. Anything else is
        // a page that does not add up, and nothing on the way there checks.
        assertEquals(100, rebalance(mix(60, 20, 20), subscribed = 40).total())
        assertEquals(100, rebalance(mix(60, 20, 20), affinity = 75).total())
        assertEquals(100, rebalance(mix(33, 33, 34), discovery = 1).total())
    }

    @Test
    fun theOtherTwoKeepTheirRatio() {
        // 20:60 stays 1:3 when the first share drops to 20 and leaves 80.
        val out = rebalance(mix(20, 20, 60), subscribed = 20)
        assertEquals(20, out.affinityPercent)
        assertEquals(60, out.discoveryPercent)
    }

    @Test
    fun aSliderReachesBothEnds() {
        // The reason the other two are rescaled rather than each slider clamped:
        // clamping makes the last few percent unreachable, and a slider that
        // stops before its end is one people push at.
        val full = rebalance(mix(60, 20, 20), subscribed = 100)
        assertEquals(100, full.subscribedPercent)
        assertEquals(100, full.total())

        val none = rebalance(mix(60, 20, 20), subscribed = 0)
        assertEquals(0, none.subscribedPercent)
        assertEquals(100, none.total())
    }

    @Test
    fun splitsEvenlyWhenThereIsNothingToScale() {
        // Both of the others at zero: dividing by their total would crash, and
        // leaving them there would lose the remainder silently.
        val out = rebalance(mix(100, 0, 0), subscribed = 50)
        assertEquals(100, out.total())
        assertEquals(25, out.affinityPercent)
        assertEquals(25, out.discoveryPercent)
    }
}
