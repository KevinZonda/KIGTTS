package com.lhtstudio.kigtts.app.ui

import com.lhtstudio.kigtts.app.data.UserPrefs
import org.junit.Assert.assertFalse
import org.junit.Test

class TopBarDefaultTest {
    @Test
    fun `theme color top bar defaults to disabled`() {
        assertFalse(UserPrefs.AppSettings().solidTopBar)
        assertFalse(UiState().solidTopBar)
    }
}
