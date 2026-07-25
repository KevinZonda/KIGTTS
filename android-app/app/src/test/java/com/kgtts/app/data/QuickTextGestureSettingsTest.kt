package com.lhtstudio.kigtts.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickTextGestureSettingsTest {
    @Test
    fun normalizedPreservesMasterAndKnownBindings() {
        val source = QuickTextGestureSettings(
            enabled = true,
            bindings = QuickTextGestures.defaultBindings().map { binding ->
                if (binding.gestureId == "z_shape") {
                    binding.copy(enabled = true, text = "请稍等一下")
                } else {
                    binding
                }
            }
        )

        val restored = source.normalized()

        assertTrue(restored.enabled)
        assertEquals("请稍等一下", restored.binding("z_shape")?.text)
        assertTrue(restored.binding("z_shape")?.enabled == true)
    }

    @Test
    fun normalizationMergesMissingDefaultsAndDropsUnknownGestures() {
        val restored = QuickTextGestureSettings(
            enabled = true,
            bindings = listOf(
                QuickTextGestureBinding("unknown", enabled = true, text = "x"),
                QuickTextGestureBinding("m_shape", enabled = true, text = "你好")
            )
        ).normalized()

        assertEquals(QuickTextGestures.templates.map { it.id }, restored.bindings.map { it.gestureId })
        assertEquals("你好", restored.binding("m_shape")?.text)
        assertTrue(restored.binding("m_shape")?.enabled == true)
        assertFalse(restored.bindings.any { it.gestureId == "unknown" })
    }

    @Test
    fun activeBindingsRequireEnabledAndNonBlankText() {
        val settings = QuickTextGestureSettings(
            enabled = true,
            bindings = listOf(
                QuickTextGestureBinding("m_shape", enabled = true, text = " "),
                QuickTextGestureBinding("w_shape", enabled = true, text = "触发内容")
            )
        )

        assertEquals(listOf("w_shape"), settings.activeBindings().map { it.gestureId })
    }
}
