package com.lhtstudio.kigtts.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

class PttTranscriptMergerTest {
    @Test
    fun finalTranscriptReplacesPreviewWhenOnlyPunctuationDiffers() {
        assertEquals(
            "今天，天气怎么样？",
            PttTranscriptMerger.merge("今天天气怎么样", "今天，天气怎么样？")
        )
    }

    @Test
    fun longerFinalTranscriptReplacesShortPreview() {
        assertEquals(
            "今天天气怎么样？我们出去走走。",
            PttTranscriptMerger.merge("今天天气怎么样", "今天天气怎么样？我们出去走走。")
        )
    }

    @Test
    fun overlappingSegmentsAreJoinedOnce() {
        assertEquals(
            "你好呀今天天气不错",
            PttTranscriptMerger.merge("你好呀", "呀今天天气不错")
        )
    }

    @Test
    fun punctuationAtSegmentBoundaryDoesNotRepeatFirstCharacter() {
        assertEquals(
            "你好呀，今天天气不错。",
            PttTranscriptMerger.merge("你好呀，", "呀，今天天气不错。")
        )
    }

    @Test
    fun englishSegmentsKeepAWordBoundary() {
        assertEquals(
            "hello world",
            PttTranscriptMerger.merge("hello", "world")
        )
    }

    @Test
    fun confirmedDictationSegmentsRemainConcatenated() {
        val first = PttTranscriptMerger.merge("", "第一句已经确认。")
        val second = PttTranscriptMerger.merge(first, "第二句也确认了。")

        assertEquals("第一句已经确认。第二句也确认了。", second)
    }

    @Test
    fun rollingTranscriptCollapsesRepeatedShorterClauses() {
        assertEquals(
            "很高兴。",
            PttTranscriptMerger.mergeRolling("", "很高兴。高兴。很高兴。高兴。")
        )
    }

    @Test
    fun rollingTranscriptKeepsSlidingWindowContinuation() {
        assertEquals(
            "今天天气很好我们出去走走。",
            PttTranscriptMerger.mergeRolling("今天天气很好", "天气很好我们出去走走。")
        )
    }

    @Test
    fun listeningPreviewUsesTheLatestCompleteUtteranceDecode() {
        assertEquals(
            "今天天气很好我们出去走走。",
            PttTranscriptMerger.updateListeningPreview(
                "今天天气很好",
                "今天天气很好我们出去走走。"
            )
        )
    }

    @Test
    fun listeningPreviewDoesNotKeepAStaleLongerDecode() {
        assertEquals(
            "本次较短但更新的识别。",
            PttTranscriptMerger.updateListeningPreview(
                "此前较长但已经失效的识别内容。",
                "本次较短但更新的识别。"
            )
        )
    }

    @Test
    fun listeningPreviewReplacesUnrelatedRollingWindow() {
        assertEquals(
            "新的窗口内容。",
            PttTranscriptMerger.updateListeningPreview(
                "旧窗口已经很长，而且不应该继续累积。",
                "新的窗口内容。"
            )
        )
    }

    @Test
    fun listeningPreviewDoesNotAccumulateSuccessiveRollingWindows() {
        val first = PttTranscriptMerger.updateListeningPreview("", "第一段窗口内容。")
        val second = PttTranscriptMerger.updateListeningPreview(first, "第二段窗口内容。")
        val third = PttTranscriptMerger.updateListeningPreview(second, "第三段窗口内容。")

        assertEquals("第三段窗口内容。", third)
    }

    @Test
    fun listeningCaptionUsesStreamingTextWhenFinalDecodeIsEmpty() {
        assertEquals(
            "这段内容已经显示在流式字幕中。",
            PttTranscriptMerger.finalizeListeningCaption(
                "这段内容已经显示在流式字幕中。",
                ""
            )
        )
    }

    @Test
    fun listeningCaptionPrefersPunctuatedFinalText() {
        assertEquals(
            "今天，天气怎么样？",
            PttTranscriptMerger.finalizeListeningCaption(
                "今天天气怎么样",
                "今天，天气怎么样？"
            )
        )
    }

    @Test
    fun listeningCaptionUsesFinalDecodeWithoutAppendingStalePreview() {
        assertEquals(
            "这是本次完整的最终结果。",
            PttTranscriptMerger.finalizeListeningCaption(
                "旧预览。旧预览。错误滚动窗口。",
                "这是本次完整的最终结果。"
            )
        )
    }

    @Test
    fun recognizesFallbackPreviewAsTheSameFinalUtterance() {
        assertEquals(
            true,
            PttTranscriptMerger.isSameRollingUtterance(
                "今天，天气",
                "今天天气怎么样？"
            )
        )
    }
}
