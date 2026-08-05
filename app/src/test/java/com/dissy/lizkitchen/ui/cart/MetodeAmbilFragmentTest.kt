package com.dissy.lizkitchen.ui.cart

import com.dissy.lizkitchen.utility.METODE_AMBIL_SENDIRI
import com.dissy.lizkitchen.utility.METODE_PESAN_ANTAR
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetodeAmbilFragmentTest {
    @Test
    fun methodSelectionRequiresAvailableDeliveryOrPickupBranch() {
        assertTrue(isMethodSelectionValid(METODE_PESAN_ANTAR, false, true))
        assertFalse(isMethodSelectionValid(METODE_PESAN_ANTAR, false, false))
        assertTrue(isMethodSelectionValid(METODE_AMBIL_SENDIRI, true, false))
        assertFalse(isMethodSelectionValid(METODE_AMBIL_SENDIRI, false, true))
        assertFalse(isMethodSelectionValid("", false, true))
    }
}
