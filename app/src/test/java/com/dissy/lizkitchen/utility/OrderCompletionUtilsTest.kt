package com.dissy.lizkitchen.utility

import org.junit.Assert.assertEquals
import org.junit.Test

class OrderCompletionUtilsTest {
    @Test
    fun receiptConfirmationRemainingLabel_formatsDeadlineWindow() {
        val hour = 60L * 60L * 1_000L
        assertEquals("2 hari 5 jam", receiptConfirmationRemainingLabel((2L * 24L + 5L) * hour))
        assertEquals("3 jam", receiptConfirmationRemainingLabel(3L * hour))
        assertEquals("kurang dari 1 jam", receiptConfirmationRemainingLabel(30L * 60L * 1_000L))
        assertEquals("Tenggat sudah lewat", receiptConfirmationRemainingLabel(0L))
    }
}
