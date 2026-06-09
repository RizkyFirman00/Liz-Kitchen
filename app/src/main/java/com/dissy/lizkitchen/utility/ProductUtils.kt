package com.dissy.lizkitchen.utility

import com.dissy.lizkitchen.model.Cake
import com.dissy.lizkitchen.model.ProductCategory

fun normalizeProductUnit(value: String?): String {
    val normalized = value.orEmpty().trim().lowercase()
    return if (normalized == "gram" || normalized == "gr" || normalized == "g") "gram" else "pcs"
}

fun formatProductPrice(value: String): String {
    val cleanValue = value.filter { it.isDigit() }
    if (cleanValue.isEmpty()) return ""

    val stringBuilder = StringBuilder(cleanValue)
    var i = stringBuilder.length - 3
    while (i > 0) {
        stringBuilder.insert(i, ".")
        i -= 3
    }
    return stringBuilder.toString()
}

fun productPriceToLong(value: String): Long {
    return value.filter { it.isDigit() }.toLongOrNull() ?: 0
}

fun Cake.availableCategories(): List<ProductCategory> {
    return kategoriProduk.ifEmpty {
        listOf(
            ProductCategory(
                namaKategori = kategori.ifBlank { "Default" },
                harga = harga,
                stok = stok,
                satuan = normalizeProductUnit(satuan)
            )
        )
    }
}

fun Cake.primaryCategory(): ProductCategory {
    return availableCategories().first()
}

fun Cake.displayNameWithCategory(): String {
    return if (kategori.isBlank() || kategori == "Default") {
        namaKue
    } else {
        "$namaKue - $kategori"
    }
}

fun Cake.displayUnit(): String {
    return if (kategori.isBlank() || kategori == "Default") {
        normalizeProductUnit(satuan)
    } else {
        kategori
    }
}

fun ProductCategory.displayUnit(): String {
    return if (namaKategori.isBlank() || namaKategori == "Default") {
        normalizeProductUnit(satuan)
    } else {
        namaKategori
    }
}

fun ProductCategory.toFirestoreMap(): Map<String, Any> {
    return mapOf(
        "namaKategori" to namaKategori.ifBlank { "Default" },
        "harga" to harga,
        "stok" to stok,
        "satuan" to normalizeProductUnit(satuan)
    )
}

fun productCategoryFromMap(map: Map<*, *>): ProductCategory {
    return ProductCategory(
        namaKategori = map["namaKategori"]?.toString().orEmpty().ifBlank { "Default" },
        harga = formatProductPrice(map["harga"]?.toString().orEmpty()),
        stok = (map["stok"] as? Number)?.toLong() ?: map["stok"]?.toString()?.toLongOrNull() ?: 0,
        satuan = normalizeProductUnit(map["satuan"]?.toString())
    )
}

fun productCategoriesFromAny(value: Any?): List<ProductCategory> {
    return (value as? List<*>)?.mapNotNull { item ->
        (item as? Map<*, *>)?.let { productCategoryFromMap(it) }
    }.orEmpty()
}

fun cakeFromMap(documentId: String, map: Map<*, *>): Cake {
    val categories = productCategoriesFromAny(map["kategoriProduk"])
    val fallbackUnit = normalizeProductUnit(map["satuan"]?.toString())
    val fallbackHarga = formatProductPrice(map["harga"]?.toString().orEmpty())
    val fallbackStok = (map["stok"] as? Number)?.toLong() ?: map["stok"]?.toString()?.toLongOrNull() ?: 0
    val primary = categories.firstOrNull()

    return Cake(
        documentId = map["documentId"]?.toString().orEmpty().ifBlank { documentId },
        harga = primary?.harga ?: fallbackHarga,
        imageUrl = map["imageUrl"]?.toString().orEmpty(),
        namaKue = map["namaKue"]?.toString().orEmpty(),
        stok = primary?.stok ?: fallbackStok,
        satuan = primary?.satuan ?: fallbackUnit,
        kategori = primary?.namaKategori ?: map["kategori"]?.toString().orEmpty(),
        kategoriProduk = categories
    )
}

fun parseProductCategoryInput(
    value: String,
    fallbackPrice: String,
    fallbackStock: Long,
    fallbackUnit: String
): List<ProductCategory> {
    val defaultCategory = ProductCategory(
        namaKategori = "Default",
        harga = formatProductPrice(fallbackPrice),
        stok = fallbackStock,
        satuan = normalizeProductUnit(fallbackUnit)
    )

    if (value.isBlank()) return listOf(defaultCategory)

    return value.lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapIndexed { index, line ->
            val parts = line.split("|").map { it.trim() }
            val name = parts.getOrNull(0).orEmpty()
            val price = formatProductPrice(parts.getOrNull(1).orEmpty().ifBlank { fallbackPrice })
            val stock = parts.getOrNull(2)?.toLongOrNull() ?: fallbackStock
            val unit = normalizeProductUnit(parts.getOrNull(3).orEmpty().ifBlank { fallbackUnit })

            if (name.isBlank() || price.isBlank()) {
                throw IllegalArgumentException("Format kategori baris ${index + 1} belum lengkap")
            }

            ProductCategory(name, price, stock, unit)
        }
}

fun productCategoriesToInput(categories: List<ProductCategory>): String {
    return categories.joinToString("\n") { category ->
        "${category.namaKategori} | ${category.harga} | ${category.stok} | ${normalizeProductUnit(category.satuan)}"
    }
}

fun cartDocumentId(cakeId: String, categoryName: String): String {
    val safeCategory = categoryName.lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
        .ifBlank { "default" }
    return "${cakeId}_$safeCategory"
}
