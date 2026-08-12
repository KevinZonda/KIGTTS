package com.lhtstudio.kigtts.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickSubtitleCandidateLayoutTest {
    @Test
    fun gridSpanCountMatchesPreviousAdaptiveGridAtDialogWidths() {
        assertEquals(1, quickSubtitleCandidateGridSpanCount(180f))
        assertEquals(2, quickSubtitleCandidateGridSpanCount(320f))
        assertEquals(3, quickSubtitleCandidateGridSpanCount(520f))
        assertEquals(4, quickSubtitleCandidateGridSpanCount(650f))
    }

    @Test
    fun gridSpanCountNeverDropsBelowOneBeforeMeasurement() {
        assertEquals(1, quickSubtitleCandidateGridSpanCount(0f))
    }
}
