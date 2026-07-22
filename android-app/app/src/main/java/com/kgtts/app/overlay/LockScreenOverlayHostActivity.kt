package com.lhtstudio.kigtts.app.overlay

import android.app.Activity
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import com.lhtstudio.kigtts.app.util.AppLogger

internal class LockScreenOverlayHostActivity : Activity() {
    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_USER_PRESENT,
                Intent.ACTION_SCREEN_OFF,
                ACTION_DISMISS -> finishWithoutAnimation()
            }
        }
    }
    private var receiverRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(false)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        window.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.TRANSPARENT
        }
        setContentView(View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isFocusableInTouchMode = true
            setOnClickListener { finishWithoutAnimation() }
            requestFocus()
        })
        registerCloseReceiver()
        if (!isKeyguardLocked()) finishWithoutAnimation()
    }

    override fun onResume() {
        super.onResume()
        if (!isKeyguardLocked()) finishWithoutAnimation()
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            runCatching { unregisterReceiver(closeReceiver) }
            receiverRegistered = false
        }
        super.onDestroy()
        overridePendingTransition(0, 0)
    }

    private fun registerCloseReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(ACTION_DISMISS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(closeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(closeReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun isKeyguardLocked(): Boolean =
        getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    private fun finishWithoutAnimation() {
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val ACTION_DISMISS =
            "com.lhtstudio.kigtts.app.action.DISMISS_LOCK_SCREEN_OVERLAY_HOST"

        fun showIfLocked(context: Context, reason: String) {
            val keyguard = context.getSystemService(KeyguardManager::class.java)
            if (keyguard?.isKeyguardLocked != true || !FloatingOverlayService.canDrawOverlays(context)) {
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
