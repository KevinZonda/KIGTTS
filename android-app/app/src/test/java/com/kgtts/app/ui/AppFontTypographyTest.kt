package com.lhtstudio.kigtts.app.ui

import com.lhtstudio.kigtts.app.data.AppFontFamilySource
import com.lhtstudio.kigtts.app.data.AppFontFileSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFontTypographyTest {
    private val source = AppFontFamilySource(
        files = listOf(AppFontFileSource("/fonts/example.ttf", 400)),
        defaultWeight = 400
    )

    @Test
    fun loadStateOnlyResolvesItsExactFontRequest() {
        val state = AppFontFamilyLoadState(
            source = source,
            preferredWeight = 500,
            fontFamily = null,
            resolved = true
        )

        assertTrue(state.isResolvedFor(source, 500))
        assertFalse(state.isResolvedFor(source, 400))
        assertFalse(state.isResolvedFor(null, 500))
    }

    @Test
    fun unresolvedFontRequestKeepsStartupBlocked() {
        val state = AppFontFamilyLoadState(
            source = source,
            preferredWeight = 400,
            fontFamily = null,
            resolved = false
        )

        assertFalse(state.isResolvedFor(source, 400))
    }
}
