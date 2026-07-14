package com.dissy.lizkitchen.utility

import com.dissy.lizkitchen.model.Cake
import com.dissy.lizkitchen.model.Cart
import com.dissy.lizkitchen.model.Order
import com.dissy.lizkitchen.model.User
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Locale

const val ORDER_STATUS_PENDING_PAYMENT = "Menunggu Pembayaran"
const val ORDER_STATUS_CONFIRMED = "Sudah Dikonfirmasi"
const val ORDER_STATUS_PROCESSING = "Sedang Diproses"
const val ORDER_STATUS_SHIPPING = "Sedang Dikirim"
const val ORDER_STATUS_READY_PICKUP = "Siap Diambil"
const val ORDER_STATUS_DONE = "Selesai"
const val ORDER_STATUS_CANCELED = "Dibatalkan"
const val ORDER_STATUS_EXPIRED = "Expired"
const val PAYMENT_EXPIRY_DURATION_MILLIS = 24L * 60L * 60L * 1_000L

fun orderFromDocument(document: DocumentSnapshot): Order {
    return Order(
        cart = cartItemsFromAny(document.get("cart")),
        orderId = document.getString("orderId").orEmpty().ifBlank { document.id },
        status = document.getString("status").orEmpty(),
        metodePengambilan = document.getString("metodePengambilan").orEmpty(),
        pickupBranchId = document.getString("pickupBranchId").orEmpty(),
        pickupBranchName = document.getString("pickupBranchName").orEmpty(),
        pickupBranchAddress = document.getString("pickupBranchAddress").orEmpty(),
        patokanAlamat = document.getString("patokanAlamat").orEmpty(),
        tanggalOrder = document.getString("tanggalOrder").orEmpty(),
        jamOrder = document.getString("jamOrder").orEmpty(),
        createdAtMillis = numberToLong(document.get("createdAtMillis")),
        paymentDeadlineMillis = numberToLong(document.get("paymentDeadlineMillis")),
        totalPrice = numberToLong(document.get("totalPrice")),
        user = userFromAny(document.get("user"))
    )
}

fun orderToFirestoreMap(order: Order): Map<String, Any> {
    return mapOf(
        "cart" to order.cart.map { cartToFirestoreMap(it) },
        "orderId" to order.orderId,
        "status" to order.status,
        "metodePengambilan" to order.metodePengambilan,
        "pickupBranchId" to order.pickupBranchId,
        "pickupBranchName" to order.pickupBranchName,
        "pickupBranchAddress" to order.pickupBranchAddress,
        "patokanAlamat" to order.patokanAlamat,
        "tanggalOrder" to order.tanggalOrder,
        "jamOrder" to order.jamOrder,
        "createdAtMillis" to order.createdAtMillis,
        "paymentDeadlineMillis" to order.paymentDeadlineMillis,
        "totalPrice" to order.totalPrice,
        "user" to userToFirestoreMap(order.user)
    )
}

fun orderPaymentDeadlineMillis(order: Order): Long {
    if (order.paymentDeadlineMillis > 0L) return order.paymentDeadlineMillis

    val createdAtMillis = orderCreatedAtMillis(order)
    return if (createdAtMillis > 0L) {
        createdAtMillis + PAYMENT_EXPIRY_DURATION_MILLIS
    } else {
        0L
    }
}

fun isPendingPaymentExpired(order: Order, nowMillis: Long = System.currentTimeMillis()): Boolean {
    if (order.status != ORDER_STATUS_PENDING_PAYMENT) return false

    val deadlineMillis = orderPaymentDeadlineMillis(order)
    return deadlineMillis > 0L && nowMillis >= deadlineMillis
}

fun orderWithPaymentExpiry(order: Order, nowMillis: Long = System.currentTimeMillis()): Order {
    return if (isPendingPaymentExpired(order, nowMillis)) {
        order.copy(
            status = ORDER_STATUS_EXPIRED,
            paymentDeadlineMillis = orderPaymentDeadlineMillis(order)
        )
    } else {
        order
    }
}

fun validateOrderExpiryOnRead(
    db: FirebaseFirestore,
    order: Order,
    nowMillis: Long = System.currentTimeMillis()
): Order {
    if (!isPendingPaymentExpired(order, nowMillis)) return order

    updateExpiredOrderDocuments(db, order, nowMillis)
    return orderWithPaymentExpiry(order, nowMillis)
}

private fun updateExpiredOrderDocuments(
    db: FirebaseFirestore,
    order: Order,
    nowMillis: Long
) {
    val orderId = order.orderId.ifBlank { return }
    val globalOrderRef = db.collection("orders").document(orderId)
    val userId = order.user.userId.orEmpty()

    db.runTransaction { transaction ->
        val currentSnapshot = transaction.get(globalOrderRef)
        val currentOrder = if (currentSnapshot.exists()) {
            orderFromDocument(currentSnapshot)
        } else {
            order
        }

        if (!isPendingPaymentExpired(currentOrder, nowMillis)) {
            return@runTransaction null
        }

        val updates = buildExpiredOrderUpdates(currentOrder, nowMillis)
        transaction.set(globalOrderRef, updates, SetOptions.merge())

        if (userId.isNotBlank()) {
            val userOrderRef = db.collection("users").document(userId)
                .collection("orders").document(orderId)
            transaction.set(userOrderRef, updates, SetOptions.merge())
        }

        null
    }
}

private fun buildExpiredOrderUpdates(order: Order, nowMillis: Long): MutableMap<String, Any> {
    val createdAtMillis = orderCreatedAtMillis(order)
    val deadlineMillis = orderPaymentDeadlineMillis(order)
    val updates = mutableMapOf<String, Any>(
        "status" to ORDER_STATUS_EXPIRED,
        "paymentDeadlineMillis" to deadlineMillis,
        "expiredAtMillis" to nowMillis,
        "expiredBy" to "client_read"
    )

    if (order.createdAtMillis <= 0L && createdAtMillis > 0L) {
        updates["createdAtMillis"] = createdAtMillis
    }

    return updates
}

private fun orderCreatedAtMillis(order: Order): Long {
    if (order.createdAtMillis > 0L) return order.createdAtMillis

    val orderIdMillis = order.orderId.removePrefix("ORDER-").toLongOrNull()
    if (orderIdMillis != null && orderIdMillis > 0L) return orderIdMillis

    return parseOrderDateTimeMillis(order.tanggalOrder, order.jamOrder)
}

private fun parseOrderDateTimeMillis(date: String, time: String): Long {
    val cleanDate = date.trim()
    if (cleanDate.isBlank()) return 0L

    val cleanTime = time.trim().ifBlank { "00:00:00" }
    val dateTime = "$cleanDate $cleanTime"
    return listOf("dd-MM-yyyy HH:mm:ss", "dd-MM-yyyy HH:mm")
        .firstNotNullOfOrNull { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.getDefault()).parse(dateTime)?.time
            }.getOrNull()
        } ?: 0L
}

fun cartItemsFromAny(value: Any?): List<Cart> {
    return (value as? List<*>)?.mapNotNull { item ->
        val map = item as? Map<*, *> ?: return@mapNotNull null
        val cakeMap = map["cake"] as? Map<*, *> ?: emptyMap<String, Any>()
        val cakeId = map["cakeId"]?.toString().orEmpty()
            .ifBlank { cakeMap["documentId"]?.toString().orEmpty() }

        Cart(
            cakeId = cakeId,
            cake = cakeFromMap(cakeId, cakeMap),
            jumlahPesanan = numberToLong(map["jumlahPesanan"])
        )
    }.orEmpty()
}

fun cartToFirestoreMap(cart: Cart): Map<String, Any> {
    return mapOf(
        "cakeId" to cart.cakeId,
        "cake" to cakeToFirestoreMap(cart.cake),
        "jumlahPesanan" to cart.jumlahPesanan
    )
}

fun userFromAny(value: Any?): User {
    val map = value as? Map<*, *>
    return User(
        alamat = map?.get("alamat")?.toString().orEmpty(),
        email = map?.get("email")?.toString().orEmpty(),
        name = map?.get("name")?.toString().orEmpty()
            .ifBlank { map?.get("username")?.toString().orEmpty() },
        phoneNumber = map?.get("phoneNumber")?.toString().orEmpty(),
        userId = map?.get("userId")?.toString().orEmpty()
    )
}

fun userToFirestoreMap(user: User): Map<String, Any> {
    return mapOf(
        "alamat" to user.alamat.orEmpty(),
        "email" to user.email.orEmpty(),
        "name" to user.name.orEmpty(),
        "phoneNumber" to user.phoneNumber.orEmpty(),
        "userId" to user.userId.orEmpty()
    )
}

fun isInvalidCheckoutAddress(value: String?): Boolean {
    val normalized = value.orEmpty().trim()
    return normalized.isBlank() ||
            normalized.equals("Belum ada alamat", ignoreCase = true) ||
            normalized.equals("Belum diisi", ignoreCase = true) ||
            normalized.equals("Belum Diisi", ignoreCase = true) ||
            normalized.equals("Address", ignoreCase = true)
}

private fun cakeToFirestoreMap(cake: Cake): Map<String, Any> {
    return mapOf(
        "documentId" to cake.documentId,
        "harga" to cake.harga,
        "imageUrl" to cake.imageUrl,
        "namaKue" to cake.namaKue,
        "stok" to cake.stok,
        "satuan" to cake.satuan,
        "kategori" to cake.kategori,
        "kategoriProduk" to cake.kategoriProduk.map { it.toFirestoreMap() }
    )
}

private fun numberToLong(value: Any?): Long {
    return (value as? Number)?.toLong() ?: value?.toString()?.toLongOrNull() ?: 0
}
