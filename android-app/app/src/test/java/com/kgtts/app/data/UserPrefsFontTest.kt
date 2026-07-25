package com.lhtstudio.kigtts.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun floatingOverlayUsesSelectedAppFontByDefault() {
        assertFalse(UserPrefs.AppSettings().floatingOverlayUseSystemFont)
    }

    @Test
    fun customTextToolbarRemainsDefaultForExistingUsers() {
        assertFalse(UserPrefs.AppSettings().useSystemTextToolbar)
    }

    @Test
    fun floatingOverlayFabUsesVoicePriorityByDefault() {
        assertFalse(UserPrefs.AppSettings().floatingOverlayFabPrefersKeyboard)
        assertFalse(UserPrefs.AppSettings().floatingOverlayFabInputGuideShown)
    }

    @Test
    fun fontDownloadSourcesDefaultToModelScope() {
        val settings = UserPrefs.AppSettings()

        assertEquals(UserPrefs.APP_FONT_SOURCE_MODELSCOPE, settings.appFontPreferredSource)
        assertEquals(
            AppFontRemoteSource.ModelScope.defaultRepositoryBaseUrl,
            settings.appFontModelScopeUrl
        )
        assertEquals(
            AppFontRemoteSource.HuggingFace.defaultRepositoryBaseUrl,
            settings.appFontHuggingFaceUrl
        )
    }

    @Test
    fun normalizesAndValidatesFontRepositoryBaseUrls() {
        val source = AppFontRemoteSource.HuggingFace
        val custom = " https://example.com/fonts/font_manifest.json/ "

        assertEquals("https://example.com/fonts", source.normalizeRepositoryBaseUrl(custom))
        assertTrue(source.isValidRepositoryBaseUrl(custom))
        assertFalse(source.isValidRepositoryBaseUrl("https://example.com/fonts?token=test"))
        assertEquals(
            source.defaultRepositoryBaseUrl,
            source.resolvedRepositoryBaseUrl("not a repository URL")
        )
    }
}
