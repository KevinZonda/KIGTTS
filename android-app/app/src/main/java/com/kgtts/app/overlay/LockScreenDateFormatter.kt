package com.lhtstudio.kigtts.app.overlay

import android.content.Context
import android.os.Build
import com.lhtstudio.kigtts.app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object LockScreenDateFormatter {
    private val monthNames = arrayOf(
        "正月", "二月", "三月", "四月", "五月", "六月",
        "七月", "八月", "九月", "十月", "冬月", "腊月"
    )
    private val dayNames = arrayOf(
        "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
        "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
        "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
    )

    fun lunarLabel(month: Int, day: Int, leapMonth: Boolean): String? {
        val monthName = monthNames.getOrNull(month - 1) ?: return null
        val dayName = dayNames.getOrNull(day - 1) ?: return null
        return "农历${if (leapMonth) "闰" else ""}$monthName$dayName"
    }

    fun currentLabel(context: Context, showLunarDate: Boolean, now: Date = Date()): String {
        val solarDate = SimpleDateFormat(
            context.getString(R.string.lock_screen_date_pattern),
            Locale.getDefault()
        ).format(now)
        val lunarDate = if (showLunarDate && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching {
                val calendar = android.icu.util.ChineseCalendar().apply { time = now }
                lunarLabel(
                    month = calendar.get(android.icu.util.Calendar.MONTH) + 1,
                    day = calendar.get(android.icu.util.Calendar.DAY_OF_MONTH),
                    leapMonth = calendar.get(android.icu.util.Calendar.IS_LEAP_MONTH) == 1
                )
            }.getOrNull()
        } else {
            null
        }
        return if (lunarDate.isNullOrBlank()) solarDate else "$solarDate · $lunarDate"
    }
}
