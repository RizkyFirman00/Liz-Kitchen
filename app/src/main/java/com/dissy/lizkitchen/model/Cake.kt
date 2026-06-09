package com.dissy.lizkitchen.model

data class Cake @JvmOverloads constructor(
    val documentId: String = "",
    val harga: String = "",
    val imageUrl: String = "",
    val namaKue: String = "",
    val stok: Long = 0,
    val satuan: String = "pcs",
    val kategori: String = "",
    val kategoriProduk: List<ProductCategory> = emptyList(),
)

data class ProductCategory @JvmOverloads constructor(
    val namaKategori: String = "",
    val harga: String = "",
    val stok: Long = 0,
    val satuan: String = "pcs",
)
