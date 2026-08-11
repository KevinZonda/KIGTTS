package com.lhtstudio.kigtts.app.overlay

import android.app.Activity
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.lhtstudio.kigtts.app.R
import com.lhtstudio.kigtts.app.data.UserPrefs
import com.lhtstudio.kigtts.app.data.LockScreenWallpaperStore
import com.lhtstudio.kigtts.app.theme.ThemeColorResolver
import com.lhtstudio.kigtts.app.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal class LockScreenOverlayHostActivity : Activity() {
    private val clockHandler = Handler(Looper.getMainLooper())
    private val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_USER_PRESENT,
                Intent.ACTION_SCREEN_OFF,
                ACTION_DISMISS -> finishWithoutAnimation()
            }
        }
    }
    private val dateRefreshTask = object : Runnable {
        override fun run() {
            updateDate()
            val delayMs = 60_000L - (System.currentTimeMillis() % 60_000L)
            clockHandler.postDelayed(this, delayMs)
        }
    }
    private val keyguardStateCheckTask = object : Runnable {
        override fun run() {
            checkKeyguardState()
        }
    }
    private val hostRevealFallbackTask = Runnable {
        if (!hostEntryRevealed) {
            AppLogger.w("Lock screen host entry reveal used timeout fallback")
            revealHostWithAnimation()
        }
    }

    private lateinit var root: FrameLayout
    private lateinit var timeView: TextClock
    private lateinit var dateView: TextView
    private lateinit var batteryIcon: TextView
    private lateinit var batteryGroup: LinearLayout
    private lateinit var batteryView: TextView
    private lateinit var wallpaperView: ImageView
    private lateinit var unlockHint: LinearLayout
    private lateinit var unlockIcon: TextView
    private lateinit var unlockText: TextView
    private lateinit var unlockController: LockScreenUnlockController
    private lateinit var batteryController: LockScreenBatteryController
    private lateinit var layoutController: LockScreenHostLayoutController
    private lateinit var appearanceController: LockScreenHostAppearanceController
    private var lockOverlayBinder: LockScreenFloatingOverlayService.LocalBinder? = null
    private var receiverRegistered = false
    private var lockOverlayBound = false
    private var showLunarDate = false
    private var hostAppearancePrepared = false
    private var hostEntryRevealed = false
    private val lockOverlayConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? LockScreenFloatingOverlayService.LocalBinder ?: return
            lockOverlayBinder = binder
            root.post {
                val token = window.decorView.windowToken ?: return@post
                binder.attachHost(
                    token = token,
                    unlockRequester = { action -> unlockController.runAfterUnlock(action) },
                    miniVisibilityListener = layoutController::setMiniOverlayVisible,
                    listeningVisibilityListener = layoutController::setListeningOverlayVisible,
                    listeningTopClearanceListener = layoutController::setListeningTopClearance,
                    entryRevealListener = { root.post(::revealHostWithAnimation) }
                )
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            lockOverlayBinder = null
            lockOverlayBound = false
            revealHostWithAnimation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLockScreenWindow()
        createContent()
        batteryController.start()
        registerCloseReceiver()
        clockHandler.post(dateRefreshTask)
        loadHostAppearance()
        checkKeyguardState()
    }

    override fun onResume() {
        super.onResume()
        checkKeyguardState()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        layoutController.apply()
        appearanceController.onConfigurationChanged()
    }

    @Suppress("DEPRECATION")
    override fun onDestroy() {
        clockHandler.removeCallbacksAndMessages(null)
        hostScope.cancel()
        if (lockOverlayBound) {
            lockOverlayBinder?.detachHost()
            runCatching { unbindService(lockOverlayConnection) }
            lockOverlayBound = false
        }
        lockOverlayBinder = null
        if (::batteryController.isInitialized) batteryController.stop()
        if (::appearanceController.isInitialized) appearanceController.dispose()
        if (receiverRegistered) {
            runCatching { unregisterReceiver(closeReceiver) }
            receiverRegistered = false
        }
        super.onDestroy()
        overridePendingTransition(0, 0)
    }

    private fun configureLockScreenWindow() {
        setShowWhenLocked(true)
        setTurnScreenOn(false)
        window.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                attributes = attributes.apply {
                    layoutInDisplayCutoutMode =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                        } else {
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                        }
                }
            }
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                isNavigationBarContrastEnforced = false
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setHideOverlayWindows(true)
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    private fun applySystemBarVisibility() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, root).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        root.requestApplyInsets()
    }

    private fun createContent() {
        wallpaperView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
            isClickable = false
            isFocusable = false
        }
        timeView = TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 68f)
            setShadowLayer(dp(3).toFloat(), 0f, dp(1).toFloat(), 0xB3000000.toInt())
            setOnClickListener { openClockAfterUnlock() }
        }
        dateView = TextView(this).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), 0xB3000000.toInt())
            setOnClickListener { openCalendarAfterUnlock() }
        }
        batteryView = TextView(this).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), 0xB3000000.toInt())
        }
        batteryIcon = TextView(this).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = runCatching {
                ResourcesCompat.getFont(this@LockScreenOverlayHostActivity, R.font.material_symbols_sharp)
            }.getOrNull()
            text = "battery_android_full"
        }
        batteryGroup = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            addView(
                batteryIcon,
                LinearLayout.LayoutParams(dp(20), dp(20))
            )
            addView(
                batteryView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(4) }
            )
        }
        unlockIcon = TextView(this).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 25f)
            typeface = runCatching {
                ResourcesCompat.getFont(this@LockScreenOverlayHostActivity, R.font.material_symbols_sharp)
            }.getOrNull()
            text = getString(R.string.lock_screen_unlock_icon)
            setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), 0xB3000000.toInt())
        }
        unlockText = TextView(this).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            text = getString(R.string.lock_screen_unlock_hint)
            setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), 0xB3000000.toInt())
        }
        unlockHint = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(
                unlockIcon,
                LinearLayout.LayoutParams(dp(36), dp(32))
            )
            addView(
                unlockText,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        unlockController = LockScreenUnlockController(
            activity = this,
            hint = unlockHint,
            dp = ::dp,
            onUnlocked = ::finishWithoutAnimation
        )
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            clipToPadding = false
            alpha = 0f
            visibility = View.VISIBLE
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = getString(R.string.lock_screen_content_description)
            isFocusableInTouchMode = true
            setOnTouchListener(unlockController::onTouch)
            requestFocus()
        }
        setContentView(root)
        layoutController = LockScreenHostLayoutController(
            context = this,
            root = root,
            backgroundView = wallpaperView,
            timeView = timeView,
            dateView = dateView,
            batteryGroup = batteryGroup,
            batteryView = batteryView,
            unlockHint = unlockHint,
            unlockIcon = unlockIcon,
            unlockText = unlockText,
            dp = ::dp
        )
        batteryController = LockScreenBatteryController(
            context = this,
            batteryContainer = batteryGroup,
            batteryIcon = batteryIcon,
            batteryView = batteryView,
            onVisibilityChanged = layoutController::setBatteryVisible
        )
        appearanceController = LockScreenHostAppearanceController(
            context = this,
            wallpaperView = wallpaperView,
            timeView = timeView,
            dateView = dateView,
            batteryIcon = batteryIcon,
            batteryView = batteryView,
            unlockIcon = unlockIcon,
            unlockText = unlockText,
            layoutController = layoutController,
            dp = ::dp,
            onShowLunarDateChanged = { enabled ->
                showLunarDate = enabled
                updateDate()
            },
            onSystemBarAppearanceChanged = ::applySystemBarAppearance
        )
        var lastHostWidth = 0
        var lastHostHeight = 0
        root.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            val width = right - left
            val height = bottom - top
            if (width == lastHostWidth && height == lastHostHeight) return@addOnLayoutChangeListener
            lastHostWidth = width
            lastHostHeight = height
            root.post { layoutController.apply() }
        }
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.navigationBars())
                updateRootInsets(view, bars.left, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                updateRootInsets(
                    view,
                    insets.systemWindowInsetLeft,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom
                )
            }
            insets
        }
        layoutController.apply()
    }

    private fun updateRootInsets(view: View, left: Int, right: Int, bottom: Int) {
        if (view.paddingLeft == left && view.paddingRight == right && view.paddingBottom == bottom) return
        view.setPadding(left, 0, right, bottom)
        view.post { layoutController.apply() }
    }

    private fun bindLockOverlayInstance() {
        if (lockOverlayBound) return
        lockOverlayBound = runCatching {
            bindService(
                Intent(this, LockScreenFloatingOverlayService::class.java),
                lockOverlayConnection,
                Context.BIND_AUTO_CREATE
            )
        }.onFailure {
            AppLogger.e("Lock screen overlay instance bind failed", it)
        }.getOrDefault(false)
    }

    private fun loadHostAppearance() {
        hostScope.launch {
            var revealHost = true
            try {
                val settings = withContext(Dispatchers.IO) {
                    runCatching { UserPrefs.getSettings(this@LockScreenOverlayHostActivity) }
                        .getOrDefault(UserPrefs.AppSettings())
                }
                val systemDark =
                    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                        Configuration.UI_MODE_NIGHT_YES
                val dark = UserPrefs.resolveThemeMode(settings.overlayThemeMode, systemDark)
                val roles = ThemeColorResolver.resolve(
                    seedArgb = settings.themeColorArgb,
                    darkTheme = dark,
                    toneCorrectionEnabled = settings.themeToneCorrectionEnabled
                )
                val lockSettings = settings.lockScreenSettings
                batteryController.applySettings(lockSettings)
                val baseTypefaces = OverlayTypefaceLoader.load(
                    this@LockScreenOverlayHostActivity,
                    OverlayTypefaceRequest(
                        useSystemFont = lockSettings.useSystemFont,
                        appFontId = settings.appFontId,
                        preferredWeight = settings.appFontWeight
                    )
                )
                val separateClockTypefaces = if (lockSettings.useSeparateClockFont) {
                    OverlayTypefaceLoader.load(
                        this@LockScreenOverlayHostActivity,
                        OverlayTypefaceRequest(
                            useSystemFont = lockSettings.clockFontId ==
                                com.lhtstudio.kigtts.app.data.AppFontDefaults.SystemFontId,
                            appFontId = lockSettings.clockFontId,
                            preferredWeight = lockSettings.clockFontWeight
                        )
                    )
                } else {
                    null
                }
                val metrics = resources.displayMetrics
                val wallpaper = LockScreenWallpaperStore.loadForDisplay(
                    lockSettings.wallpaperPath,
                    metrics.widthPixels,
                    metrics.heightPixels,
                    lockSettings.wallpaperBlurRadius
                )
                appearanceController.apply(
                    lockSettings,
                    baseTypefaces,
                    separateClockTypefaces,
                    wallpaper,
                    dark,
                    roles.primaryArgb
                )
            } catch (error: CancellationException) {
                revealHost = false
                throw error
            } catch (error: Throwable) {
                AppLogger.e("Lock screen appearance load failed", error)
            } finally {
                if (revealHost) revealPreparedHost()
            }
        }
    }

    private fun revealPreparedHost() {
        if (isFinishing || isDestroyed || !isKeyguardLocked()) return
        applySystemBarVisibility()
        root.animate().cancel()
        root.alpha = 0f
        root.visibility = View.VISIBLE
        root.post {
            if (isFinishing || isDestroyed || !isKeyguardLocked()) return@post
            hostAppearancePrepared = true
            bindLockOverlayInstance()
            clockHandler.removeCallbacks(hostRevealFallbackTask)
            clockHandler.postDelayed(hostRevealFallbackTask, HOST_ENTRY_REVEAL_FALLBACK_MS)
        }
    }

    private fun revealHostWithAnimation() {
        if (
            !hostAppearancePrepared ||
            hostEntryRevealed ||
            isFinishing ||
            isDestroyed ||
            !isKeyguardLocked()
        ) {
            return
        }
        hostEntryRevealed = true
        clockHandler.removeCallbacks(hostRevealFallbackTask)
        root.animate().cancel()
        root.alpha = 0f
        root.visibility = View.VISIBLE
        root.animate()
            .alpha(1f)
            .setDuration(HOST_ENTRY_REVEAL_DURATION_MS)
            .start()
    }

    private fun applySystemBarAppearance(dark: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                if (dark) 0 else
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (dark) {
                window.decorView.systemUiVisibility and
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            } else {
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        }
    }

    private fun updateDate() {
        dateView.text = LockScreenDateFormatter.currentLabel(this, showLunarDate)
    }

    private fun openClockAfterUnlock() {
        unlockController.runAfterUnlock {
            if (!LockScreenSystemAppLauncher.openClock(this)) {
                Toast.makeText(this, R.string.lock_screen_clock_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openCalendarAfterUnlock() {
        unlockController.runAfterUnlock {
            if (!LockScreenSystemAppLauncher.openCalendar(this)) {
                Toast.makeText(this, R.string.lock_screen_calendar_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun registerCloseReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(ACTION_DISMISS)
        }
        ContextCompat.registerReceiver(
            this,
            closeReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    private fun isKeyguardLocked(): Boolean =
        getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    private fun checkKeyguardState() {
        clockHandler.removeCallbacks(keyguardStateCheckTask)
        if (!isKeyguardLocked()) {
            finishWithoutAnimation()
        } else if (!isFinishing && !isDestroyed) {
            clockHandler.postDelayed(
                keyguardStateCheckTask,
                KEYGUARD_STATE_CHECK_INTERVAL_MS
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun finishWithoutAnimation() {
        if (isFinishing || isDestroyed) return
        clockHandler.removeCallbacks(keyguardStateCheckTask)
        if (::root.isInitialized) {
            root.animate().cancel()
            root.alpha = 0f
            root.visibility = View.INVISIBLE
        }
        clockHandler.removeCallbacks(hostRevealFallbackTask)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        finishAndRemoveTask()
        overridePendingTransition(0, 0)
    }

    private fun dp(value: Int): Int =
        (resources.displayMetrics.density * value).roundToInt()

    companion object {
        private const val KEYGUARD_STATE_CHECK_INTERVAL_MS = 400L
        private const val HOST_ENTRY_REVEAL_DURATION_MS = 220L
        private const val HOST_ENTRY_REVEAL_FALLBACK_MS = 2_000L
        private const val ACTION_DISMISS =
            "com.lhtstudio.kigtts.app.action.DISMISS_LOCK_SCREEN_OVERLAY_HOST"
        fun showIfLocked(context: Context, reason: String) {
            val keyguard = context.getSystemService(KeyguardManager::class.java)
            if (keyguard?.isKeyguardLocked != true) {
                return
            }
            runCatching {
                context.startActivity(
                    Intent(context, LockScreenOverlayHostActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                                Intent.FLAG_ACTIVITY_NO_ANIMATION
                        )
                    }
                )
                AppLogger.i("LockScreenOverlayHostActivity shown: $reason")
            }.onFailure { error ->
                AppLogger.e("LockScreenOverlayHostActivity start failed: $reason", error)
            }
        }

        fun dismiss(context: Context) {
            context.sendBroadcast(
                Intent(ACTION_DISMISS).setPackage(context.packageName)
            )
        }
    }

}
