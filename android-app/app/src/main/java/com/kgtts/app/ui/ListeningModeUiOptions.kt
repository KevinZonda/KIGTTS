package com.lhtstudio.kigtts.app.ui

import com.lhtstudio.kigtts.app.audio.AudioRoutePreference

internal val listeningInputOptions = listOf(
    AudioRoutePreference.INPUT_BUILTIN_MIC to "内置麦克风/话筒",
    AudioRoutePreference.INPUT_AUTO to "自动",
    AudioRoutePreference.INPUT_USB to "USB 麦克风",
    AudioRoutePreference.INPUT_BLUETOOTH to "蓝牙麦克风",
    AudioRoutePreference.INPUT_WIRED to "有线麦克风"
)
