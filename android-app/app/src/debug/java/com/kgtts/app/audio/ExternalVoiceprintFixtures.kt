package com.lhtstudio.kigtts.app.audio

import org.json.JSONObject
import java.io.File

internal data class MatrixVoiceSource(
    val id: String,
    val displayName: String,
    val origin: String,
    val clip: DebugAudioClip,
    val isOwner: Boolean,
    val scenarios: List<SimulatedAudioScenario>,
    val enrollmentClips: List<DebugAudioClip> = emptyList()
)

internal data class ExternalVoiceprintFixtureSet(
    val phrase: String?,
    val sources: List<MatrixVoiceSource>,
    val issues: List<String>
)

internal object ExternalVoiceprintFixtures {
    const val DIRECTORY_NAME = "synthetic_voice_matrix"
    private const val MANIFEST_FILE_NAME = "manifest.json"

    fun load(directory: File): ExternalVoiceprintFixtureSet {
        val manifest = File(directory, MANIFEST_FILE_NAME)
        if (!manifest.isFile) {
            return ExternalVoiceprintFixtureSet(null, emptyList(), emptyList())
        }
        val root = JSONObject(manifest.readText(Charsets.UTF_8))
        require(root.optInt("version", 1) == 1) { "Unsupported external fixture manifest version" }
        val phrase = root.optString("phrase").trim().ifBlank { null }
        val voices = root.optJSONArray("voices")
            ?: return ExternalVoiceprintFixtureSet(phrase, emptyList(), listOf("Manifest has no voices array"))
        val sources = mutableListOf<MatrixVoiceSource>()
        val issues = mutableListOf<String>()
        repeat(voices.length()) { index ->
            runCatching {
                parseVoice(directory, voices.getJSONObject(index), index)
            }.onSuccess(sources::add).onFailure { error ->
                issues += "voice[$index]: ${error.message ?: error.javaClass.simpleName}"
            }
        }
        return ExternalVoiceprintFixtureSet(phrase, sources, issues)
    }

    private fun parseVoice(
        directory: File,
        entry: JSONObject,
        index: Int
    ): MatrixVoiceSource {
        val id = entry.optString("id").trim().ifBlank { "external_$index" }
        val name = entry.optString("name").trim().ifBlank { id }
        val origin = entry.optString("origin").trim().ifBlank { "external_wav" }
        val fileName = entry.getString("file").trim()
        require(fileName == File(fileName).name && fileName.endsWith(".wav", ignoreCase = true)) {
            "External fixture must be a local WAV file name"
        }
        val wave = File(directory, fileName)
        require(wave.isFile) { "Missing WAV file $fileName" }
        val scenarios = entry.optJSONArray("scenarios")?.let { values ->
            val ids = buildList {
                repeat(values.length()) { scenarioIndex ->
                    add(values.getString(scenarioIndex))
                }
            }
            SimulatedAudioScenario.parse(ids.joinToString(","))
        } ?: listOf(SimulatedAudioScenario.CLEAN)
        val clip = SimulatedAudioFixtures.readPcm16Wave(wave)
        val enrollmentClips = entry.optJSONArray("enrollment_files")?.let { values ->
            buildList {
                repeat(values.length()) { enrollmentIndex ->
                    val item = values.getJSONObject(enrollmentIndex)
                    val enrollmentFileName = item.getString("file").trim()
                    require(
                        enrollmentFileName == File(enrollmentFileName).name &&
                            enrollmentFileName.endsWith(".wav", ignoreCase = true)
                    ) { "Enrollment fixture must be a local WAV file name" }
                    val enrollmentWave = File(directory, enrollmentFileName)
                    require(enrollmentWave.isFile) { "Missing WAV file $enrollmentFileName" }
                    add(
                        SimulatedAudioFixtures.readPcm16Wave(enrollmentWave).copy(
                            sourceLabel = "external-enrollment:$origin:$name:${enrollmentIndex + 1}"
                        )
                    )
                }
            }
        }.orEmpty()
        return MatrixVoiceSource(
            id = id,
            displayName = name,
            origin = origin,
            clip = clip.copy(sourceLabel = "external:$origin:$name"),
            isOwner = false,
            scenarios = scenarios,
            enrollmentClips = enrollmentClips
        )
    }
}
