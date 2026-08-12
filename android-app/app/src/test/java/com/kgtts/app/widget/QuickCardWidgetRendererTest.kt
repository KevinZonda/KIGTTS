package com.lhtstudio.kigtts.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickCardWidgetRendererTest {
    @Test
    fun portraitWatermarkKeepsRotatedGlyphInsideRightEdge() {
        val width = 420
        val height = 680
        val ascent = -92f
        val renderScale = 1.4f

        val layout = QuickCardWidgetRenderer.quickCardPortraitWatermarkLayout(
            width = width,
            height = height,
            textAscent = ascent,
            renderScale = renderScale
        )

        val transformedBaselineX = -layout.drawBaseline
        val rotatedGlyphRight = transformedBaselineX - ascent
        assertEquals(width - 10f * renderScale, rotatedGlyphRight, 0.01f)
        assertEquals(10f * renderScale, layout.drawX, 0.01f)
        assertTrue(layout.drawX + layout.maxWidth <= height)
    }
}
