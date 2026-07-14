package com.dissy.lizkitchen.utility

import org.junit.Assert.assertEquals
import org.junit.Test

class ProductUtilsTest {
    @Test
    fun normalizeProductUnit_preservesCustomProductUnit() {
        assertEquals("toples", normalizeProductUnit(" Toples "))
        assertEquals("gram", normalizeProductUnit("gr"))
        assertEquals("pcs", normalizeProductUnit(null))
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
        assertEquals("gram", cake.kategoriProduk.first().satuan)
    }
}
