package com.lhtstudio.kigtts.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickCardLayoutTest {
    @Test
    fun noteLinesUseQrRegionHeightInsteadOfSingleLine() {
        assertEquals(9, quickCardNoteMaxLines(regionHeightDp = 180f, hasTitle = true))
        assertEquals(11, quickCardNoteMaxLines(regionHeightDp = 180f, hasTitle = false))
    }

    @Test
    fun noteAlwaysKeepsAtLeastOneVisibleLine() {
        assertEquals(1, quickCardNoteMaxLines(regionHeightDp = 20f, hasTitle = true))
    }

    @Test
    fun quickCardTypesRoundTripThroughPackageWireValues() {
        QuickCardType.entries.forEach { type ->
            assertEquals(type, QuickCardType.fromWire(type.wireValue))
        }
        assertEquals(QuickCardType.Text, QuickCardType.fromWire("unknown"))
    }
}
