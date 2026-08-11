package com.lhtstudio.kigtts.app.data

object AsrRecognitionLanguage {
    const val MANDARIN = "zh"
    const val AUTO = "auto"
    const val CANTONESE = "yue"
    const val ENGLISH = "en"
    const val JAPANESE = "ja"
    const val KOREAN = "ko"

    const val DEFAULT = MANDARIN

    val entries: List<String> = listOf(
        MANDARIN,
        AUTO,
        CANTONESE,
        ENGLISH,
        JAPANESE,
        KOREAN
    )

    fun normalize(value: String?): String {
        val normalized = value?.trim()?.lowercase().orEmpty()
        return when (normalized) {
            MANDARIN, "cmn", "zh-cn", "zh-hans" -> MANDARIN
            AUTO -> AUTO
            CANTONESE, "zh-yue" -> CANTONESE
            ENGLISH, "en-us", "en-gb" -> ENGLISH
            JAPANESE, "jp" -> JAPANESE
            KOREAN, "kr" -> KOREAN
            else -> DEFAULT
        }
    }

    fun label(value: String?): String = when (normalize(value)) {
        AUTO -> "自动判断"
        CANTONESE -> "中文（粤语）"
        ENGLISH -> "英语"
        JAPANESE -> "日语"
        KOREAN -> "韩语"
        else -> "中文（普通话）"
    }

    fun description(value: String?): String = when (normalize(value)) {
        AUTO -> "自动判断当前语音的主要语言，适合多语言交替使用。"
        CANTONESE -> "按粤语识别，普通话或其他语言内容的准确率可能下降。"
        ENGLISH -> "按英语识别，中文或其他语言内容的准确率可能下降。"
        JAPANESE -> "按日语识别，中文或其他语言内容的准确率可能下降。"
        KOREAN -> "按韩语识别，中文或其他语言内容的准确率可能下降。"
        else -> "按普通话识别，适合以中文为主的使用场景。"
    }
}
