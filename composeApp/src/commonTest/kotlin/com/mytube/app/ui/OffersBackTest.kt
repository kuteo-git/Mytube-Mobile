package com.mytube.app.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OffersBackTest {

    /**
     * Measured on the phone: four drags from the start of the seek bar, each
     * cancelled the moment it began, because iOS's back is a pan from the left
     * edge and an enabled back cancels every touch starting there.
     */
    @Test
    fun theExpandedVideoOffersNoBackWhereBackIsAnEdgeSwipe() {
        assertFalse(offersBack(watchExpanded = true, somewhereToGo = true, edgeSwipeIsBack = true))
        assertFalse(offersBack(watchExpanded = true, somewhereToGo = false, edgeSwipeIsBack = true))
    }

    /** Android's back is a button or a system gesture, and collapses the video as it always did. */
    @Test
    fun theExpandedVideoCollapsesOnBackWhereBackIsNotAnEdgeSwipe() {
        assertTrue(offersBack(watchExpanded = true, somewhereToGo = false, edgeSwipeIsBack = false))
    }

    @Test
    fun withoutTheVideoBackIsOfferedWhenThereIsSomewhereToGo() {
        assertTrue(offersBack(watchExpanded = false, somewhereToGo = true, edgeSwipeIsBack = true))
        assertTrue(offersBack(watchExpanded = false, somewhereToGo = true, edgeSwipeIsBack = false))
        assertFalse(offersBack(watchExpanded = false, somewhereToGo = false, edgeSwipeIsBack = true))
        assertFalse(offersBack(watchExpanded = false, somewhereToGo = false, edgeSwipeIsBack = false))
    }
}
