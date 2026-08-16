package com.lhtstudio.kigtts.app.data

import org.json.JSONArray
import org.json.JSONObject

data class QuickTextGesturePoint(
    val x: Float,
    val y: Float
)

data class QuickTextGestureTemplate(
    val id: String,
    val title: String,
    val description: String,
    val points: List<QuickTextGesturePoint>
)

data class QuickTextGestureBinding(
    val gestureId: String,
    val enabled: Boolean = false,
    val text: String = ""
)

data class QuickTextGestureSettings(
    val enabled: Boolean = false,
    val bindings: List<QuickTextGestureBinding> = QuickTextGestures.defaultBindings()
) {
    fun normalized(): QuickTextGestureSettings {
        val knownBindings = bindings.associateBy(QuickTextGestureBinding::gestureId)
        return copy(
            bindings = QuickTextGestures.templates.map { template ->
                val binding = knownBindings[template.id]
                QuickTextGestureBinding(
                    gestureId = template.id,
                    enabled = binding?.enabled ?: false,
                    text = binding?.text.orEmpty().take(MAX_TEXT_LENGTH)
                )
            }
        )
    }

    fun binding(gestureId: String): QuickTextGestureBinding? =
        normalized().bindings.firstOrNull { it.gestureId == gestureId }

    fun updateBinding(
        gestureId: String,
        transform: (QuickTextGestureBinding) -> QuickTextGestureBinding
    ): QuickTextGestureSettings = copy(
        bindings = normalized().bindings.map { binding ->
            if (binding.gestureId == gestureId) transform(binding) else binding
        }
    ).normalized()

    fun activeBindings(): List<QuickTextGestureBinding> = normalized().bindings.filter {
        it.enabled && it.text.isNotBlank()
    }

    companion object {
        const val MAX_TEXT_LENGTH = 1000
    }
}

object QuickTextGestures {
    val templates: List<QuickTextGestureTemplate> = listOf(
        template(
            id = "m_shape",
            title = "M 形",
            description = "从左下开始，一笔画出 M 形。",
            0.10f to 0.86f,
            0.10f to 0.16f,
            0.50f to 0.70f,
            0.90f to 0.16f,
            0.90f to 0.86f
        ),
        template(
            id = "w_shape",
            title = "W 形",
            description = "从左上开始，一笔画出 W 形。",
            0.10f to 0.16f,
            0.10f to 0.86f,
            0.50f to 0.30f,
            0.90f to 0.86f,
            0.90f to 0.16f
        ),
        template(
            id = "z_shape",
            title = "Z 形",
            description = "从左上向右画，再斜向左下，最后向右收笔。",
            0.10f to 0.18f,
            0.90f to 0.18f,
            0.10f to 0.84f,
            0.90f to 0.84f
        ),
        template(
            id = "lightning",
            title = "闪电",
            description = "从右上开始，沿折线一笔画到左下。",
            0.82f to 0.10f,
            0.30f to 0.46f,
            0.68f to 0.46f,
            0.18f to 0.90f
        ),
        template(
            id = "hourglass",
            title = "沙漏",
            description = "从左上斜到右下，再向上折回左下。",
            0.12f to 0.14f,
            0.88f to 0.86f,
            0.88f to 0.14f,
            0.12f to 0.86f
        ),
        template(
            id = "folded_hook",
            title = "折钩",
            description = "从左上向下，沿底部折向右上，再向内收笔。",
            0.16f to 0.12f,
            0.16f to 0.78f,
            0.32f to 0.90f,
            0.70f to 0.90f,
            0.86f to 0.72f,
            0.86f to 0.42f,
            0.66f to 0.26f
        )
    )

    fun defaultBindings(): List<QuickTextGestureBinding> = templates.map { template ->
        QuickTextGestureBinding(gestureId = template.id)
    }

    fun template(gestureId: String): QuickTextGestureTemplate? =
        templates.firstOrNull { it.id == gestureId }

    private fun template(
        id: String,
        title: String,
        description: String,
        vararg points: Pair<Float, Float>
    ) = QuickTextGestureTemplate(
        id = id,
        title = title,
        description = description,
        points = points.map { (x, y) -> QuickTextGesturePoint(x, y) }
    )
}

internal fun encodeQuickTextGestureSettings(settings: QuickTextGestureSettings): String {
    val normalized = settings.normalized()
    return JSONObject().apply {
        put("version", 1)
        put("enabled", normalized.enabled)
        put(
            "bindings",
            JSONArray().apply {
                normalized.bindings.forEach { binding ->
                    put(
                        JSONObject().apply {
                            put("gestureId", binding.gestureId)
                            put("enabled", binding.enabled)
                            put("text", binding.text)
                        }
                    )
                }
            }
        )
    }.toString()
}

internal fun decodeQuickTextGestureSettings(raw: String?): QuickTextGestureSettings {
    if (raw.isNullOrBlank()) return QuickTextGestureSettings()
    return runCatching {
        val json = JSONObject(raw)
        val bindingsJson = json.optJSONArray("bindings") ?: JSONArray()
        val bindings = buildList {
            for (index in 0 until bindingsJson.length()) {
                val value = bindingsJson.optJSONObject(index) ?: continue
                val gestureId = value.optString("gestureId")
                if (gestureId.isBlank()) continue
                add(
                    QuickTextGestureBinding(
                        gestureId = gestureId,
                        enabled = value.optBoolean("enabled", false),
                        text = value.optString("text", "")
                    )
                )
            }
        }
        QuickTextGestureSettings(
            enabled = json.optBoolean("enabled", false),
            bindings = bindings
        ).normalized()
    }.getOrDefault(QuickTextGestureSettings())
}
