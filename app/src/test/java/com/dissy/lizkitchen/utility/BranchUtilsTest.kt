package com.dissy.lizkitchen.utility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BranchUtilsTest {
    @Test
    fun deliveryFeeForDistanceMeters_usesConfiguredTiers() {
        assertEquals(0L, deliveryFeeForDistanceMeters(5_000f))
        assertEquals(15_000L, deliveryFeeForDistanceMeters(5_001f))
        assertEquals(15_000L, deliveryFeeForDistanceMeters(10_000f))
        assertEquals(30_000L, deliveryFeeForDistanceMeters(10_001f))
        assertEquals(30_000L, deliveryFeeForDistanceMeters(20_000f))
        assertEquals(45_000L, deliveryFeeForDistanceMeters(20_001f))
        assertEquals(60_000L, deliveryFeeForDistanceMeters(40_001f))
        assertEquals(80_000L, deliveryFeeForDistanceMeters(60_001f))
        assertEquals(100_000L, deliveryFeeForDistanceMeters(80_001f))
        assertEquals(100_000L, deliveryFeeForDistanceMeters(100_000f))
    }

    @Test
    fun deliveryFeeForDistanceMeters_rejectsDistanceAboveMaximum() {
        assertNull(deliveryFeeForDistanceMeters(100_001f))
    }
}
