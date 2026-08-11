package com.lhtstudio.kigtts.app.data

object SpeechButtonActionMode {
    const val TOGGLE = 0
    const val HOLD = 1
    const val HOLD_CONFIRM = 2

    val entries: List<Int> = listOf(TOGGLE, HOLD, HOLD_CONFIRM)

    fun normalize(value: Int): Int = value.coerceIn(TOGGLE, HOLD_CONFIRM)

    fun fromLegacy(pushToTalkEnabled: Boolean, confirmEnabled: Boolean): Int = when {
        !pushToTalkEnabled -> TOGGLE
        confirmEnabled -> HOLD_CONFIRM
        else -> HOLD
    }

    fun usesPushToTalk(value: Int): Boolean = normalize(value) != TOGGLE

    fun usesConfirmation(value: Int): Boolean = normalize(value) == HOLD_CONFIRM

    fun label(value: Int): String = when (normalize(value)) {
        HOLD -> "按住说话"
        HOLD_CONFIRM -> "按住并确认"
        else -> "点击开关"
    }

    fun description(value: Int): String = when (normalize(value)) {
        HOLD -> "按住按钮时收音，松手后直接发送识别结果。"
        HOLD_CONFIRM -> "按住时预览识别结果，松手前可选择发送、转到输入框或取消。"
        else -> "点击语音识别按钮开始或停止持续识别。"
    }
}
