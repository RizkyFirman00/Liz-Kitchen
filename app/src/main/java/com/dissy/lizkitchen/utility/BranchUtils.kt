package com.dissy.lizkitchen.utility

import android.location.Location
import com.dissy.lizkitchen.model.Order

const val METODE_AMBIL_SENDIRI = "Ambil Sendiri"
const val METODE_PESAN_ANTAR = "Pesan Antar"
const val MAX_DELIVERY_RADIUS_METERS = 5_000f

const val LIZ_KITCHEN_BRANCH_ADDRESS =
    "Jl. Kebon Sirih Barat I No.24, RT.2/RW.2, Kb. Sirih, Kec. Menteng, Kota Jakarta Pusat, DKI Jakarta 10340"

data class LizKitchenBranch(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
) {
    fun toLocation(): Location {
        return Location("liz_kitchen_$id").apply {
            latitude = this@LizKitchenBranch.latitude
            longitude = this@LizKitchenBranch.longitude
        }
    }
}

val LIZ_KITCHEN_BRANCHES = listOf(
    LizKitchenBranch(
        id = "menteng",
        name = "Cabang Menteng",
        address = LIZ_KITCHEN_BRANCH_ADDRESS,
        latitude = -6.1844710,
        longitude = 106.8266899
    ),
    LizKitchenBranch(
        id = "cengkareng",
        name = "Cabang Cengkareng",
        address = "The City Resort Malibu, Blk. A - B No.10, RT.7/RW.14, Cengkareng Tim., Kecamatan Cengkareng, Kota Jakarta Barat, Daerah Khusus Ibukota Jakarta 11730",
        latitude = -6.1369292,
        longitude = 106.7355049
    )
)

val DEFAULT_LIZ_KITCHEN_BRANCH: LizKitchenBranch = LIZ_KITCHEN_BRANCHES.first()

fun branchById(branchId: String?): LizKitchenBranch? {
    return LIZ_KITCHEN_BRANCHES.firstOrNull { it.id == branchId }
}

fun pickupBranchForOrder(order: Order): LizKitchenBranch? {
    return branchById(order.pickupBranchId)
        ?: LIZ_KITCHEN_BRANCHES.firstOrNull { it.address == order.pickupBranchAddress }
        ?: LIZ_KITCHEN_BRANCHES.firstOrNull { it.name == order.pickupBranchName }
}

fun pickupBranchNameForOrder(order: Order): String {
    return order.pickupBranchName.ifBlank {
        pickupBranchForOrder(order)?.name ?: DEFAULT_LIZ_KITCHEN_BRANCH.name
    }
}

fun pickupBranchAddressForOrder(order: Order): String {
    return order.pickupBranchAddress.ifBlank {
        pickupBranchForOrder(order)?.address ?: DEFAULT_LIZ_KITCHEN_BRANCH.address
    }
}

fun metodePengambilanDisplayForOrder(order: Order): String {
    val method = order.metodePengambilan
    return if (method.contains("ambil", ignoreCase = true)) {
        "$METODE_AMBIL_SENDIRI - ${pickupBranchNameForOrder(order)}"
    } else {
        method
    }
}

fun branchLocationLabel(branch: LizKitchenBranch): String {
    return when (branch.id) {
        "menteng" -> "Kebon Sirih, Jakarta Pusat"
        "cengkareng" -> "The City Resort Malibu"
        else -> branch.address
    }
}

fun nearestBranchDistanceMeters(location: Location): Pair<LizKitchenBranch, Float>? {
    return LIZ_KITCHEN_BRANCHES
        .map { branch -> branch to location.distanceTo(branch.toLocation()) }
        .minByOrNull { (_, distance) -> distance }
}

fun isWithinDeliveryRadius(location: Location): Boolean {
    val nearestBranch = nearestBranchDistanceMeters(location) ?: return false
    return nearestBranch.second <= MAX_DELIVERY_RADIUS_METERS
}
