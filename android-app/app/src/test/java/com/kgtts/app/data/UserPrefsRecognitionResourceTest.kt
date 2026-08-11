package com.lhtstudio.kigtts.app.data

import com.lhtstudio.kigtts.app.audio.SpeakerVerificationTolerance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPrefsRecognitionResourceTest {
    @Test
    fun speakerVerificationDefaultsToSmartTolerance() {
        assertEquals(
            SpeakerVerificationTolerance.SMART.index,
            UserPrefs.AppSettings().speakerVerifyToleranceLevel
        )
    }

    @Test
    fun automaticRecognitionPipelineIsEnabledByDefault() {
        assertEquals(
            UserPrefs.RECOGNITION_MODULE_MODE_EXPERIMENTAL,
            UserPrefs.AppSettings().recognitionModuleMode
        )
    }

    @Test
    fun recognitionLanguageDefaultsToMandarin() {
        assertEquals(
            AsrRecognitionLanguage.MANDARIN,
            UserPrefs.AppSettings().asrRecognitionLanguage
        )
    }

    @Test
    fun defaultsUseStableV6RecognitionResourcePackage() {
        assertTrue(
            UserPrefs.DEFAULT_RECOGNITION_RESOURCE_MODELSCOPE_URL.endsWith(
                "kigtts-recognition-resources-v6-20260810.7z"
            )
        )
        assertTrue(
            UserPrefs.DEFAULT_RECOGNITION_RESOURCE_HUGGINGFACE_URL.endsWith(
                "kigtts-recognition-resources-v6-20260810.7z"
            )
        )
    }

    @Test
    fun previousExperimentalBuiltInUrlsMigrateToV6Package() {
        assertEquals(
            UserPrefs.DEFAULT_RECOGNITION_RESOURCE_MODELSCOPE_URL,
            UserPrefs.normalizeRecognitionResourceModelScopeUrl(
                "https://modelscope.cn/models/LHTSTUDIO/KIGTTS_ASR_Resource/resolve/master/" +
                    "kigtts-recognition-resources-experimental-20260808.7z"
            )
        )
        assertEquals(
            UserPrefs.DEFAULT_RECOGNITION_RESOURCE_HUGGINGFACE_URL,
            UserPrefs.normalizeRecognitionResourceHuggingFaceUrl(
                "https://huggingface.co/LHT02/KIGTTS_ASR_Resource/resolve/main/" +
                    "kigtts-recognition-resources-experimental-20260808.7z"
            )
        )
    }

    @Test
    fun v4BuiltInUrlsMigrateToV6Package() {
        assertEquals(
            UserPrefs.DEFAULT_RECOGNITION_RESOURCE_MODELSCOPE_URL,
            UserPrefs.normalizeRecognitionResourceModelScopeUrl(
                "https://modelscope.cn/models/LHTSTUDIO/KIGTTS_ASR_Resource/resolve/master/" +
                    "kigtts-recognition-resources-experimental-v4-20260808.7z"
            )
        )
        assertEquals(
            UserPrefs.DEFAULT_RECOGNITION_RESOURCE_HUGGINGFACE_URL,
            UserPrefs.normalizeRecognitionResourceHuggingFaceUrl(
                "https://huggingface.co/LHT02/KIGTTS_ASR_Resource/resolve/main/" +
                    "kigtts-recognition-resources-experimental-v4-20260808.7z"
            )
        )
    }

    @Test
    fun v5BuiltInUrlsMigrateToV6Package() {
        assertEquals(
            UserPrefs.DEFAULT_RECOGNITION_RESOURCE_MODELSCOPE_URL,
            UserPrefs.normalizeRecognitionResourceModelScopeUrl(
                "https://modelscope.cn/models/LHTSTUDIO/KIGTTS_ASR_Resource/resolve/master/" +
                    "kigtts-recognition-resources-experimental-v5-20260809.7z"
            )
        )
        assertEquals(
            UserPrefs.DEFAULT_RECOGNITION_RESOURCE_HUGGINGFACE_URL,
            UserPrefs.normalizeRecognitionResourceHuggingFaceUrl(
                "https://huggingface.co/LHT02/KIGTTS_ASR_Resource/resolve/main/" +
                    "kigtts-recognition-resources-experimental-v5-20260809.7z"
            )
        )
    }

    @Test
    fun experimentalV6BuiltInUrlsMigrateToStableV6Package() {
        assertEquals(
            UserPrefs.DEFAULT_RECOGNITION_RESOURCE_MODELSCOPE_URL,
            UserPrefs.normalizeRecognitionResourceModelScopeUrl(
                "https://modelscope.cn/models/LHTSTUDIO/KIGTTS_ASR_Resource/resolve/master/" +
                    "kigtts-recognition-resources-experimental-v6-20260809.7z"
            )
        )
        assertEquals(
            UserPrefs.DEFAULT_RECOGNITION_RESOURCE_HUGGINGFACE_URL,
            UserPrefs.normalizeRecognitionResourceHuggingFaceUrl(
                "https://huggingface.co/LHT02/KIGTTS_ASR_Resource/resolve/main/" +
                    "kigtts-recognition-resources-experimental-v6-20260809.7z"
            )
        )
    }

    @Test
    fun legacyBuiltInUrlsMigrateToStableV6Package() {
        assertEquals(
            UserPrefs.DEFAULT_RECOGNITION_RESOURCE_MODELSCOPE_URL,
            UserPrefs.normalizeRecognitionResourceModelScopeUrl(
                "https://modelscope.cn/models/LHTSTUDIO/KIGTTS_ASR_Resource/resolve/master/" +
                    "kigtts-recognition-resources-20260505.7z"
            )
        )
        assertEquals(
            UserPrefs.DEFAULT_RECOGNITION_RESOURCE_HUGGINGFACE_URL,
            UserPrefs.normalizeRecognitionResourceHuggingFaceUrl(
                "https://huggingface.co/LHT02/KIGTTS_ASR_Resource/resolve/main/" +
                    "kigtts-recognition-resources-20260505.7z"
            )
        )
    }

    @Test
    fun customRecognitionResourceUrlsArePreserved() {
        val customModelScope = "https://example.com/custom-modelscope.7z"
        val customHuggingFace = "https://example.com/custom-huggingface.7z"

        assertEquals(
            customModelScope,
            UserPrefs.normalizeRecognitionResourceModelScopeUrl("  $customModelScope  ")
        )
        assertEquals(
            customHuggingFace,
            UserPrefs.normalizeRecognitionResourceHuggingFaceUrl("  $customHuggingFace  ")
        )
    }

}
