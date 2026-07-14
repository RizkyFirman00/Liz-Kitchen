package com.dissy.lizkitchen.model

data class Order (
    val cart: List<Cart> = listOf(),
    val orderId: String = "",
    val status: String = "",
    val metodePengambilan: String = "",
    val pickupBranchId: String = "",
    val pickupBranchName: String = "",
    val pickupBranchAddress: String = "",
    val patokanAlamat: String = "",
    val tanggalOrder: String = "",
    val jamOrder: String = "",
    val createdAtMillis: Long = 0,
    val paymentDeadlineMillis: Long = 0,
    val totalPrice: Long = 0,
    val user: User = User(),
)
