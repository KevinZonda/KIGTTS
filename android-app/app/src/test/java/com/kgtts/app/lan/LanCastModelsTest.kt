package com.lhtstudio.kigtts.app.lan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanCastModelsTest {
    @Test
    fun webOnlyFallsBackToLocalWithoutAudioClient() {
        assertEquals(
            LanCastPlaybackPlan(local = true, web = false),
            resolveLanCastPlaybackPlan(LanCastAudioOutputMode.Web, webAvailable = false)
        )
        assertEquals(
            LanCastPlaybackPlan(local = false, web = true),
            resolveLanCastPlaybackPlan(LanCastAudioOutputMode.Web, webAvailable = true)
        )
    }

    @Test
    fun bothModeKeepsLocalPlayback() {
        assertEquals(
            LanCastPlaybackPlan(local = true, web = false),
            resolveLanCastPlaybackPlan(LanCastAudioOutputMode.Both, webAvailable = false)
        )
        assertEquals(
            LanCastPlaybackPlan(local = true, web = true),
            resolveLanCastPlaybackPlan(LanCastAudioOutputMode.Both, webAvailable = true)
        )
    }

    @Test
    fun transientAudioDisconnectStaysAvailableDuringReconnectGrace() {
        assertTrue(
            isLanCastAudioClientAvailable(
                running = true,
                audioClients = 0,
                lastSeenAtMs = 10_000L,
                nowMs = 11_500L,
                reconnectGraceMs = 2_000L
            )
        )
        assertFalse(
            isLanCastAudioClientAvailable(
                running = true,
                audioClients = 0,
                lastSeenAtMs = 10_000L,
                nowMs = 12_001L,
                reconnectGraceMs = 2_000L
            )
        )
    }

    @Test
    fun selectedAddressChangesGeneratedUrl() {
        val first = LanCastAddress("wlan0:192.168.1.8", "Wi-Fi", "192.168.1.8")
        val second = LanCastAddress("rndis0:192.168.42.1", "USB 网络", "192.168.42.1")
        val status = LanCastStatus(
            running = true,
            port = 8765,
            addresses = listOf(first, second),
            selectedAddressId = second.id
        )

        assertEquals("http://192.168.42.1:8765/remote", status.url("remote"))
    }

    @Test
    fun presentationKeepsIndependentDisplayAndThemeRoles() {
        val state = LanCastPresentationState(
            themeToneCorrectionEnabled = true,
            lightThemeRoles = LanCastThemeRoles(
                primaryArgb = 0xFFFFC0CB.toInt(),
                onPrimaryArgb = 0xFF111417.toInt(),
                accentTextArgb = 0xFF8C4050.toInt()
            ),
            darkThemeRoles = LanCastThemeRoles(
                primaryArgb = 0xFF78D9DD.toInt(),
                onPrimaryArgb = 0xFF111417.toInt(),
                accentTextArgb = 0xFF78D9DD.toInt()
            ),
            compactQuickText = true,
            led = LanCastLedStyle(
                dotMatrix = false,
                adaptiveMultiLine = true,
                screenBrightness = 0.64f
            )
        )

        assertEquals(0xFFFFC0CB.toInt(), state.lightThemeRoles.primaryArgb)
        assertEquals(0xFF8C4050.toInt(), state.lightThemeRoles.accentTextArgb)
        assertEquals(0xFF78D9DD.toInt(), state.darkThemeRoles.primaryArgb)
        assertTrue(state.compactQuickText)
        assertFalse(state.led.dotMatrix)
        assertTrue(state.led.adaptiveMultiLine)
        assertEquals(0.64f, state.led.screenBrightness)
    }

    @Test
    fun castDisplayDefaultsToNormalAdaptiveText() {
        val style = LanCastLedStyle()

        assertFalse(style.dotMatrix)
        assertTrue(style.adaptiveMultiLine)
    }
}
