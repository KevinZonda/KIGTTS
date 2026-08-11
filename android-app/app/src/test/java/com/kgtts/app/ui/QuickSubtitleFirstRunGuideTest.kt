package com.lhtstudio.kigtts.app.ui

import com.lhtstudio.kigtts.app.data.resolveQuickSubtitleFirstRunGuideCompleted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickSubtitleFirstRunGuideTest {
    @Test
    fun guideUsesThreeNonInteractiveSteps() {
        val steps = quickSubtitleGuideSteps(compactControls = false)

        assertEquals(3, steps.size)
        assertEquals(setOf(QuickSubtitleGuideAnchor.QuickText), steps.first().anchors)
        assertTrue(steps.first().messages.any { "长按" in it })
        assertTrue(steps.first().callouts.any { "上滑" in it.label && "下滑" in it.label })
        assertTrue(QuickSubtitleGuideAnchor.BottomBar in steps.last().anchors)
        assertTrue(QuickSubtitleGuideAnchor.RecognitionFab in steps.last().anchors)
        assertTrue(steps.last().messages.any { "用于语音识别" in it })
        assertTrue(steps.last().messages.any { "可以从设置中再次进入使用引导" in it })
        assertTrue(QuickSubtitleGuideAnchor.TopBarMenu in steps[1].anchors)
        assertTrue(QuickSubtitleGuideAnchor.TopBarFullscreen in steps[1].anchors)
        assertTrue(QuickSubtitleGuideAnchor.ActionListening in steps[1].anchors)
        assertTrue(QuickSubtitleGuideAnchor.SubtitleDisplay in steps[1].anchors)
        assertTrue(steps[1].messages.any { "长按大字幕" in it })
        assertEquals(
            setOf(
                "大字幕（点按进入预览，长按复制文本）",
                "文本样式",
                "倒置",
                "LED",
                "清屏",
                "历史",
                "调整字体大小",
                "菜单",
                "音频设置菜单",
                "编辑",
                "全屏",
                "聆听模式"
            ),
            steps[1].callouts.map { it.label }.toSet()
        )
        assertTrue(steps.last().callouts.any { it.anchor == QuickSubtitleGuideAnchor.BottomSend })
        assertTrue(steps.last().callouts.any { it.label == "语音识别\n（需要安装资源包）" })
    }

    @Test
    fun compactGuideExplainsGroupSelectorGestures() {
        val firstStep = quickSubtitleGuideSteps(compactControls = true).first()

        assertTrue(firstStep.messages.any { "上下滑动" in it })
        assertTrue(firstStep.messages.any { "左右滑动" in it })
        assertTrue(QuickSubtitleGuideAnchor.QuickTextGroupSwitcher in firstStep.anchors)
        assertTrue(firstStep.callouts.any { it.label == "上下滑动切换分组" })
    }

    @Test
    fun guideUsesActualGestureDirectionAndHidesItWhenDisabled() {
        val reversedLandscape = quickSubtitleGuideSteps(
            compactControls = false,
            panelGesturesEnabled = true,
            panelGesturesReversed = true,
            isLandscape = true
        ).first()
        assertTrue(reversedLandscape.callouts.any { "右滑" in it.label && "左滑" in it.label })

        val disabled = quickSubtitleGuideSteps(
            compactControls = false,
            panelGesturesEnabled = false
        ).first()
        assertTrue(disabled.callouts.any { it.label == "长按打开候选列表" })
    }

    @Test
    fun landscapeInputAndSendCalloutsArePlacedAboveTheirButtons() {
        assertEquals(
            GuideCalloutPlacement.Above,
            calloutPlacement(
                QuickSubtitleGuideAnchor.RecognitionFab,
                verticalActions = false,
                isLandscape = true
            )
        )
        assertEquals(
            GuideCalloutPlacement.Above,
            calloutPlacement(
                QuickSubtitleGuideAnchor.BottomSend,
                verticalActions = false,
                isLandscape = true
            )
        )
        assertEquals(
            GuideCalloutPlacement.Left,
            calloutPlacement(
                QuickSubtitleGuideAnchor.BottomSend,
                verticalActions = false,
                isLandscape = false
            )
        )
    }

    @Test
    fun legacyUsersDoNotReceiveAFirstRunPopupAfterUpgrade() {
        assertTrue(
            resolveQuickSubtitleFirstRunGuideCompleted(
                stored = null,
                onboardingCompleted = true
            )
        )
        assertFalse(
            resolveQuickSubtitleFirstRunGuideCompleted(
                stored = null,
                onboardingCompleted = false
            )
        )
        assertFalse(
            resolveQuickSubtitleFirstRunGuideCompleted(
                stored = false,
                onboardingCompleted = true
            )
        )
    }

    @Test
    fun completedGuideCanBePresentedForManualReplay() {
        assertFalse(
            shouldPresentQuickSubtitleGuide(
                firstRunCompleted = true,
                replayRequestId = 0
            )
        )
        assertTrue(
            shouldPresentQuickSubtitleGuide(
                firstRunCompleted = true,
                replayRequestId = 1
            )
        )
        assertTrue(
            shouldPresentQuickSubtitleGuide(
                firstRunCompleted = false,
                replayRequestId = 0
            )
        )
    }
}
