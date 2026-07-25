package com.lhtstudio.kigtts.app.overlay

import android.content.Context
import android.graphics.Typeface
import com.lhtstudio.kigtts.app.data.AppFontDefaults
import com.lhtstudio.kigtts.app.data.AppFontFamilySource
import com.lhtstudio.kigtts.app.data.AppFontFileSource
import com.lhtstudio.kigtts.app.data.AppFontRepository
import com.lhtstudio.kigtts.app.data.OpenTypeFontParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal data class OverlayTypefaceRequest(
    val useSystemFont: Boolean,
    val appFontId: String,
    val preferredWeight: Int
)

internal data class OverlayTypefaces(
    val regular: Typeface,
    val bold: Typeface
)

internal object OverlayTypefaceLoader {
    suspend fun load(context: Context, request: OverlayTypefaceRequest): OverlayTypefaces? =
        withContext(Dispatchers.IO) {
            if (request.useSystemFont || request.appFontId == AppFontDefaults.SystemFontId) {
                return@withContext null
            }
            val source = AppFontRepository.resolveFontFamilySource(context, request.appFontId)
                ?: return@withContext null
            loadSource(source, request.preferredWeight)
        }

    private fun loadSource(source: AppFontFamilySource, preferredWeight: Int): OverlayTypefaces? {
        val available = source.files.mapNotNull { entry ->
            File(entry.path).takeIf { it.isFile }?.let { entry to it }
        }.distinctBy { it.first.weight }
        if (available.isEmpty()) return null

        val primaryFile = available.first().second
        val axis = runCatching { OpenTypeFontParser.parse(primaryFile).weightAxis }.getOrNull()
        if (axis != null) {
            val weights = resolveVariableOverlayFontWeights(
                axis = axis,
                sourceDefaultWeight = source.defaultWeight,
                preferredWeight = preferredWeight
            )
            return OverlayTypefaces(
                regular = buildVariableTypeface(primaryFile, weights.regular),
                bold = buildVariableTypeface(primaryFile, weights.bold)
            )
        }
        return loadStaticTypefaces(source, available, preferredWeight)
    }

    private fun loadStaticTypefaces(
        source: AppFontFamilySource,
        available: List<Pair<AppFontFileSource, File>>,
        preferredWeight: Int
    ): OverlayTypefaces {
        if (available.size == 1) {
            val base = Typeface.Builder(available.first().second).build()
            return OverlayTypefaces(
                regular = Typeface.create(base, Typeface.NORMAL),
                bold = Typeface.create(base, Typeface.BOLD)
            )
        }
        val byWeight = available.associate { it.first.weight to it.second }
        val weights = resolveStaticOverlayFontWeights(
            availableWeights = byWeight.keys.toList(),
            sourceDefaultWeight = source.defaultWeight,
            preferredWeight = preferredWeight
        )
        return OverlayTypefaces(
            regular = buildStaticTypeface(requireNotNull(byWeight[weights.regular]), weights.regular),
            bold = buildStaticTypeface(requireNotNull(byWeight[weights.bold]), weights.bold)
        )
    }

    private fun buildVariableTypeface(file: File, weight: Int): Typeface =
        Typeface.Builder(file)
            .setFontVariationSettings("'wght' $weight")
            .setWeight(weight)
            .build()

    private fun buildStaticTypeface(file: File, weight: Int): Typeface =
        Typeface.Builder(file)
            .setWeight(weight)
            .build()
}
