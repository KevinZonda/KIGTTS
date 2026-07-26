package com.lhtstudio.kigtts.app.overlay

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.widget.ImageView
import android.widget.TextClock
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.lhtstudio.kigtts.app.data.LockScreenSettings

internal class LockScreenHostAppearanceController(
    private val context: Context,
    private val wallpaperView: ImageView,
    private val timeView: TextClock,
    private val dateView: TextView,
    private val unlockIcon: TextView,
    private val unlockText: TextView,
    private val layoutController: LockScreenHostLayoutController,
    private val dp: (Int) -> Int,
    private val onShowLunarDateChanged: (Boolean) -> Unit,
    private val onSystemBarAppearanceChanged: (Boolean) -> Unit
) {
    private var wallpaperBitmap: Bitmap? = null
    private var settings = LockScreenSettings()
    private var darkTheme = true
    private var primary = Color.WHITE

    fun apply(
        settings: LockScreenSettings,
        baseTypefaces: OverlayTypefaces?,
        separateClockTypefaces: OverlayTypefaces?,
        wallpaper: Bitmap?,
        darkTheme: Boolean,
        primary: Int
    ) {
        this.settings = settings
        this.darkTheme = darkTheme
        this.primary = primary
        val baseTypeface = baseTypefaces?.regular ?: Typeface.DEFAULT
        timeView.typeface = if (settings.useSeparateClockFont) {
            separateClockTypefaces?.regular ?: Typeface.DEFAULT
        } else {
            baseTypeface
        }
        dateView.typeface = baseTypeface
        unlockText.typeface = baseTypeface
        onShowLunarDateChanged(settings.showLunarDate)
        layoutController.setTimeAndDateAlignedStart(settings.timeAndDateAlignedStart)
        setWallpaper(wallpaper)

        val useDarkContent = LockScreenWallpaperAppearance.shouldUseDarkContent(
            wallpaper,
            settings,
            isLandscape()
        )
        applyColors(darkTheme, primary, useDarkContent)
    }

    fun onConfigurationChanged() {
        applyWallpaperScrim()
        applyColors(
            darkTheme,
            primary,
            LockScreenWallpaperAppearance.shouldUseDarkContent(
                wallpaperBitmap,
                settings,
                isLandscape()
            )
        )
    }

    fun dispose() {
        wallpaperView.setImageDrawable(null)
        wallpaperView.foreground = null
        wallpaperBitmap?.recycle()
        wallpaperBitmap = null
    }

    private fun setWallpaper(wallpaper: Bitmap?) {
        wallpaperView.setImageDrawable(null)
        wallpaperBitmap?.takeIf { it !== wallpaper }?.recycle()
        wallpaperBitmap = wallpaper
        if (wallpaper == null) {
            wallpaperView.visibility = android.view.View.VISIBLE
        } else {
            wallpaperView.setImageBitmap(wallpaper)
            wallpaperView.visibility = android.view.View.VISIBLE
        }
        applyWallpaperScrim()
    }

    private fun applyWallpaperScrim() {
        wallpaperView.foreground = LockScreenWallpaperAppearance.scrimDrawable(
            settings,
            isLandscape()
        )
    }

    private fun isLandscape(): Boolean =
        context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun applyColors(darkTheme: Boolean, primary: Int, useDarkContent: Boolean) {
        val content = if (useDarkContent) 0xDE000000.toInt() else Color.WHITE
        timeView.setTextColor(content)
        dateView.setTextColor(
            if (useDarkContent) 0xB8000000.toInt() else ColorUtils.setAlphaComponent(content, 184)
        )
        unlockIcon.setTextColor(if (useDarkContent) content else primary)
        unlockText.setTextColor(
            if (useDarkContent) 0xC9000000.toInt() else ColorUtils.setAlphaComponent(content, 210)
        )
        if (useDarkContent) clearShadows() else applyLightTextShadows()
        onSystemBarAppearanceChanged(if (useDarkContent) false else darkTheme)
    }

    private fun clearShadows() {
        timeView.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        dateView.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        unlockIcon.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        unlockText.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
    }

    private fun applyLightTextShadows() {
        timeView.setShadowLayer(dp(3).toFloat(), 0f, dp(1).toFloat(), 0xB3000000.toInt())
        dateView.setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), 0xB3000000.toInt())
        unlockIcon.setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), 0xB3000000.toInt())
        unlockText.setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), 0xB3000000.toInt())
    }
}
