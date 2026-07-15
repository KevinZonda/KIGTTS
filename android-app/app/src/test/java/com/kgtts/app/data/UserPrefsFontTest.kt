package com.lhtstudio.kigtts.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UserPrefsFontTest {
    @Test
    fun normalizesFontIdsWithoutAllowingPaths() {
        assertEquals(
            "harmonyos-sans.sc",
            UserPrefs.normalizeAppFontId(" HarmonyOS-Sans.SC ")
        )
        assertEquals(
            AppFontDefaults.SystemFontId,
            UserPrefs.normalizeAppFontId("../../fonts/private")
        )
    }

    @Test
    fun clampsFontWeightToComposeRange() {
        assertEquals(1, UserPrefs.normalizeAppFontWeight(-20))
        assertEquals(400, UserPrefs.normalizeAppFontWeight(400))
        assertEquals(1000, UserPrefs.normalizeAppFontWeight(2000))
    }
}
