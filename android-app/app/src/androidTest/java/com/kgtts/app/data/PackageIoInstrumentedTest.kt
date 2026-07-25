package com.lhtstudio.kigtts.app.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PackageIoInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun quickCardPackageRoundTripsSelectedCardAndImage() {
        val sourceImage = File(context.cacheDir, "quick_card_package_test.png")
        sourceImage.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x01, 0x02))
        var packageFile: File? = null
        var importedImage: File? = null
        try {
            packageFile = QuickCardPackageIo.exportPackage(
                context,
                listOf(
                    QuickCardPackageCard(
                        id = 7L,
                        type = "qr",
                        title = "测试名片",
                        note = "多行备注",
                        themeColor = "#038387",
                        link = "https://example.com",
                        portraitImagePath = sourceImage.absolutePath,
                        landscapeImagePath = ""
                    )
                )
            )
            val packageUri = Uri.fromFile(packageFile)
            assertEquals(
                listOf(QuickCardPackageSummary(7L, "测试名片")),
                QuickCardPackageIo.inspectPackage(context, packageUri)
            )
            val imported = QuickCardPackageIo.importPackage(
                context = context,
                uri = packageUri,
                selectedIds = setOf(7L),
                nextId = 42L,
                existingTitles = setOf("测试名片")
            ).single()
            importedImage = File(imported.portraitImagePath)
            assertEquals(42L, imported.id)
            assertEquals("qr", imported.type)
            assertEquals("测试名片（导入）", imported.title)
            assertTrue(importedImage.isFile)
            assertEquals(sourceImage.readBytes().toList(), importedImage.readBytes().toList())
        } finally {
            sourceImage.delete()
            importedImage?.delete()
            packageFile?.delete()
        }
    }

    @Test
    fun minimalConfigBackupRestoresPreferencesWithoutOptionalResources() = runBlocking {
        val packageFile = AppConfigBackupIo.exportPackage(
            context,
            AppConfigBackupOptions(
                includeQuickSubtitlePresets = false,
                includeQuickCardImages = false,
                includeCurrentFont = false,
                includeSoundboard = false,
                includeVoicePacks = false
            )
        )
        try {
            val restored = AppConfigBackupIo.importPackage(context, Uri.fromFile(packageFile))
            assertTrue(restored.restoredPreferenceCount > 0)
            assertTrue(restored.components.isEmpty())
            assertEquals(0, restored.restoredFileCount)
            assertFalse(packageFile.name.endsWith(".zip"))
            assertTrue(packageFile.name.endsWith(".kigconfig"))
        } finally {
            packageFile.delete()
        }
    }
}
