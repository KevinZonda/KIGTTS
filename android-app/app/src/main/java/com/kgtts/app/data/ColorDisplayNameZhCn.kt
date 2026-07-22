package com.lhtstudio.kigtts.app.data

import java.util.Locale

internal fun formatColorHexAndNameZhCn(argb: Int): String = String.format(
    Locale.ROOT,
    "#%06X · %s",
    argb and 0x00FFFFFF,
    WindowsColorNamesZhCn.displayName(argb)
)
