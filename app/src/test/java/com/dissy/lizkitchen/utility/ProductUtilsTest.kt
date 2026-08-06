package com.dissy.lizkitchen.utility

import org.junit.Assert.assertEquals
import org.junit.Test

class ProductUtilsTest {
    @Test
    fun normalizeProductUnit_alwaysUsesToples() {
        assertEquals("toples", normalizeProductUnit(" Toples "))
        assertEquals("toples", normalizeProductUnit("gram"))
        assertEquals("toples", normalizeProductUnit(null))
    }

    @Test
    fun cakeFromMap_usesProductUnitInsteadOfFirstVariantUnit() {
        val cake = cakeFromMap(
            documentId = "cake-1",
            map = mapOf(
                "namaKue" to "Keju Kering",
                "satuan" to "toples",
                "kategoriProduk" to listOf(
                    mapOf(
                        "namaVarian" to "250 Gram",
                        "harga" to "45000",
                        "stok" to 15,
                        "satuan" to "gram"
                    )
                )
            )
        )

        assertEquals("toples", cake.satuan)
        assertEquals("toples", cake.kategoriProduk.first().satuan)
    }

    @Test
    fun normalizeNumericInput_removesLeadingZerosAndLimitsDigits() {
        assertEquals("12", normalizeNumericInput("00012", 6))
        assertEquals("0", normalizeNumericInput("000000", 6))
        assertEquals("123456", normalizeNumericInput("1234567", 6))
        assertEquals("999", normalizeNumericInput("9999", 3))
        assertEquals("45000", normalizeNumericInput("Rp 45.000", 9))
    }

    @Test
    fun productVariantNames_matchAdminDropdown() {
        assertEquals(listOf("250 gram", "500 gram", "700 gram"), PRODUCT_VARIANT_NAMES)
    }
}
