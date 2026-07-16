package com.lhtstudio.kigtts.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppFontCatalogParserTest {
    @Test
    fun nearestStaticWeightPrefersTheLighterWeightOnATie() {
        assertEquals(400, listOf(400, 500).nearestTo(450))
        assertEquals(450, listOf(100, 400, 450, 700).nearestTo(460))
    }
}
