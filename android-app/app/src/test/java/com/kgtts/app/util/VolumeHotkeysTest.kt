package com.lhtstudio.kigtts.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeHotkeysTest {
    @Test
    fun configuredTextAction_roundTripsText() {
        val action = VolumeHotkeyActionSpec(
            kind = VolumeHotkeyActions.KIND_INTERNAL,
            target = VolumeHotkeyActions.TARGET_QUICK_SUBTITLE_TEXT,
            text = "请稍等一下"
        )

        val decoded = VolumeHotkeyActions.decode(
            VolumeHotkeyActions.encode(action),
            VolumeHotkeyActions.defaultFor(VolumeHotkeySequence.UpDown)
        )

        assertEquals(action, decoded)
        assertTrue(VolumeHotkeyActions.requiresConfiguredText(decoded))
    }

    @Test
    fun miniConfiguredTextAction_requiresText() {
        val action = VolumeHotkeyActionSpec(
            kind = VolumeHotkeyActions.KIND_OVERLAY,
            target = VolumeHotkeyActions.TARGET_SEND_MINI_QUICK_SUBTITLE,
            text = "请稍等一下"
        )

        assertTrue(VolumeHotkeyActions.requiresConfiguredText(action))
    }

    @Test
    fun legacyPayloadWithoutText_remainsCompatible() {
        val decoded = VolumeHotkeyActions.decode(
            """{"kind":"internal","target":"quick_subtitle"}""",
            VolumeHotkeyActions.defaultFor(VolumeHotkeySequence.DownUp)
        )

        assertEquals("", decoded.text)
        assertEquals(VolumeHotkeyActions.TARGET_QUICK_SUBTITLE, decoded.target)
    }
}
