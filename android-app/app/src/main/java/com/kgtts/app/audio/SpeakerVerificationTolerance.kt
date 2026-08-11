package com.lhtstudio.kigtts.app.audio

import kotlin.math.abs

internal enum class SpeakerVerificationTolerance(
    val label: String,
    val primaryThreshold: Float,
    val confirmationThreshold: Float,
    val adaptiveRelaxation: Float,
    val description: String
) {
    SMART(
        label = "智能",
        primaryThreshold = 0.50f,
        confirmationThreshold = 0.60f,
        adaptiveRelaxation = 0.12f,
        description = "自动根据录制声纹调整，适合在室内、展会和头壳内等不同环境间切换。"
    ),
    LENIENT(
        label = "宽松",
        primaryThreshold = 0.40f,
        confirmationThreshold = 0.52f,
        adaptiveRelaxation = 0.02f,
        description = "适合轻声、隔着头壳或麦克风位置变化较大的场景，对本人声音变化最包容。"
    ),
    FAIRLY_LENIENT(
        label = "较宽",
        primaryThreshold = 0.45f,
        confirmationThreshold = 0.56f,
        adaptiveRelaxation = 0.04f,
        description = "适合展会、街道等较吵环境，兼顾轻声和日常说话变化。"
    ),
    BALANCED(
        label = "中等",
        primaryThreshold = 0.50f,
        confirmationThreshold = 0.60f,
        adaptiveRelaxation = 0.05f,
        description = "适合收音条件比较稳定的日常使用，在本人声音变化和过滤周围人声之间保持平衡。"
    ),
    FAIRLY_STRICT(
        label = "较严",
        primaryThreshold = 0.56f,
        confirmationThreshold = 0.64f,
        adaptiveRelaxation = 0.04f,
        description = "适合周围人声较多、说话距离较近的场景，轻声时可能需要再说一次。"
    ),
    STRICT(
        label = "严格",
        primaryThreshold = 0.62f,
        confirmationThreshold = 0.68f,
        adaptiveRelaxation = 0.03f,
        description = "适合多人近距离交谈、希望尽量只收自己声音的场景；头壳遮挡或噪声较大时会更难触发。"
    );

    val index: Int
        get() = ordinal

    companion object {
        fun fromIndex(index: Int): SpeakerVerificationTolerance =
            entries[index.coerceIn(0, entries.lastIndex)]

        fun fromThreshold(threshold: Float): SpeakerVerificationTolerance =
            entries
                .asSequence()
                .filterNot { it == SMART }
                .minByOrNull { abs(it.primaryThreshold - threshold) }
                ?: BALANCED
    }
}
