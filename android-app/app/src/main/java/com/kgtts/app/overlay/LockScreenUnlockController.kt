package com.lhtstudio.kigtts.app.overlay

import android.app.Activity
import android.app.KeyguardManager
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

internal class LockScreenUnlockController(
    private val activity: Activity,
    private val hint: View,
    private val dp: (Int) -> Int,
    private val onUnlocked: () -> Unit
) {
    private var gestureStartX = 0f
    private var gestureStartY = 0f

    fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestureStartX = event.rawX
                gestureStartY = event.rawY
                hint.animate().cancel()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - gestureStartX
                val dy = event.rawY - gestureStartY
                val progress = (maxOf(abs(dx), (-dy).coerceAtLeast(0f)) / dp(120))
                    .coerceIn(0f, 1f)
                hint.alpha = 1f - progress * 0.35f
                hint.translationY = minOf(0f, dy * 0.18f)
                hint.translationX = dx * 0.08f
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.rawX - gestureStartX
                val dy = event.rawY - gestureStartY
                if (abs(dx) >= dp(96) || dy <= -dp(96)) {
                    requestSystemUnlock()
                } else {
                    resetHint()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                resetHint()
                return true
            }
        }
        return view.performClick()
    }

    fun runAfterUnlock(action: () -> Unit) {
        val keyguard = activity.getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked != true) {
            action()
            onUnlocked()
            return
        }
        keyguard.requestDismissKeyguard(
            activity,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    action()
                    onUnlocked()
                }

                override fun onDismissCancelled() = resetHint()
                override fun onDismissError() = resetHint()
            }
        )
    }

    private fun requestSystemUnlock() {
        val keyguard = activity.getSystemService(KeyguardManager::class.java)
        keyguard?.requestDismissKeyguard(
            activity,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissCancelled() = resetHint()
                override fun onDismissError() = resetHint()
            }
        )
    }

    private fun resetHint() {
        hint.animate()
            .alpha(1f)
            .translationX(0f)
            .translationY(0f)
            .setDuration(180L)
            .start()
    }
}
