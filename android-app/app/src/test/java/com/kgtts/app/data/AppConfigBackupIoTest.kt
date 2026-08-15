package com.lhtstudio.kigtts.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConfigBackupIoTest {
    @Test
    fun `new reader accepts v1 and v2 backups`() {
        assertTrue(AppConfigBackupIo.isSupportedPackageVersion(1))
        assertTrue(AppConfigBackupIo.isSupportedPackageVersion(2))
        assertFalse(AppConfigBackupIo.isSupportedPackageVersion(0))
        assertFalse(AppConfigBackupIo.isSupportedPackageVersion(3))
    }

    @Test
    fun `only v2 uses full settings snapshot semantics`() {
        assertFalse(AppConfigBackupIo.usesFullSettingsSnapshot(1))
        assertTrue(AppConfigBackupIo.usesFullSettingsSnapshot(2))
    }

    @Test
    fun `backup includes selected app and clock fonts`() {
        val settings = UserPrefs.AppSettings(
            appFontId = "app-font",
            lockScreenSettings = LockScreenSettings(
                useSeparateClockFont = true,
                clockFontId = "clock-font"
            )
        )

        assertEquals(
            setOf("app-font", "clock-font"),
            AppConfigBackupIo.selectedFontIdsForBackup(settings, includeCurrentFont = true)
        )
        assertTrue(
            AppConfigBackupIo.selectedFontIdsForBackup(settings, includeCurrentFont = false).isEmpty()
        )
    }

    @Test
    fun `system and duplicate font ids are not packaged`() {
        val systemOnly = UserPrefs.AppSettings(
            appFontId = AppFontDefaults.SystemFontId,
            lockScreenSettings = LockScreenSettings(
                useSeparateClockFont = true,
                clockFontId = AppFontDefaults.SystemFontId
            )
        )
        val sharedFont = UserPrefs.AppSettings(
            appFontId = "shared-font",
            lockScreenSettings = LockScreenSettings(
                useSeparateClockFont = true,
                clockFontId = "shared-font"
            )
        )

        assertTrue(AppConfigBackupIo.selectedFontIdsForBackup(systemOnly, true).isEmpty())
        assertEquals(
            setOf("shared-font"),
            AppConfigBackupIo.selectedFontIdsForBackup(sharedFont, true)
        )
    }
}
