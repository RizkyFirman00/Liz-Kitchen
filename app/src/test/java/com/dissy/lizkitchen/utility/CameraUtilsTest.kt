package com.dissy.lizkitchen.utility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CameraUtilsTest {
    @Test
    fun usableCameraImage_requiresNonEmptyFile() {
        val image = File.createTempFile("camera", ".jpg")
        try {
            assertFalse(isUsableCameraImage(null))
            assertFalse(isUsableCameraImage(image))
            image.writeBytes(byteArrayOf(1))
            assertTrue(isUsableCameraImage(image))
        } finally {
            image.delete()
        }
    }
}
