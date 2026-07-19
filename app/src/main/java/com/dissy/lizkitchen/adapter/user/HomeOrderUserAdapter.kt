package com.dissy.lizkitchen.adapter.user

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dissy.lizkitchen.databinding.RvOrderUserBinding
import com.dissy.lizkitchen.model.Order
import com.dissy.lizkitchen.utility.ORDER_STATUS_CANCELED
import com.dissy.lizkitchen.utility.ORDER_STATUS_CONFIRMED
import com.dissy.lizkitchen.utility.ORDER_STATUS_DONE
import com.dissy.lizkitchen.utility.ORDER_STATUS_EXPIRED
import com.dissy.lizkitchen.utility.ORDER_STATUS_PENDING_PAYMENT
import com.dissy.lizkitchen.utility.ORDER_STATUS_PAYMENT_VERIFICATION
import com.dissy.lizkitchen.utility.ORDER_STATUS_PROCESSING
import com.dissy.lizkitchen.utility.ORDER_STATUS_READY_PICKUP
import com.dissy.lizkitchen.utility.ORDER_STATUS_SHIPPING
import com.dissy.lizkitchen.utility.metodePengambilanDisplayForOrder
import com.dissy.lizkitchen.utility.pickupBranchNameForOrder

class HomeOrderUserAdapter(private val onItemClick: (String) -> Unit) : ListAdapter<Order, HomeOrderUserAdapter.HomeUserViewHolder>(
    DiffCallback()
) {
    inner class HomeUserViewHolder(
        private val binding: RvOrderUserBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(order: Order) {
            binding.apply {
                val statusText = order.status.ifBlank { "Status belum tersedia" }
                tvStatusPesanan.text = statusText
                applyStatusStyle(statusText)

                val formatedPrice = formatAndDisplayCurrency(order.totalPrice.toString())
                tvTotalHarga.text = formatedPrice
                tvMetodePengambilan.text = metodePengambilanDisplayForOrder(order).ifBlank { "Metode belum dipilih" }
                tvTanggalOrder.text = buildDateText(order.tanggalOrder, order.jamOrder)
                tvOrderId.text = order.orderId.ifBlank { "-" }
                tvItemSummary.text = buildItemSummary(order)
                tvOrderAddress.text = buildAddressText(order)

                val orderCakeAdapter = HomeOrderUserCakeAdapter()
                rvOrderCakeUser.adapter = orderCakeAdapter
                rvOrderCakeUser.layoutManager = LinearLayoutManager(itemView.context)
                rvOrderCakeUser.isNestedScrollingEnabled = false

                val previewItems = order.cart.take(2)
                orderCakeAdapter.submitList(previewItems)
                val remainingItems = order.cart.size - previewItems.size
                tvMoreItems.visibility = if (remainingItems > 0) View.VISIBLE else View.GONE
                tvMoreItems.text = "+$remainingItems produk lainnya"

                root.setOnClickListener {
                    onItemClick.invoke(order.orderId)
                }
            }
        }
    }

    private fun RvOrderUserBinding.applyStatusStyle(status: String) {
        val (textColor, backgroundColor) = when (status) {
            ORDER_STATUS_DONE -> "#128A35" to "#E8F7EC"
            ORDER_STATUS_CANCELED, ORDER_STATUS_EXPIRED -> "#C62828" to "#FDECEC"
            ORDER_STATUS_PENDING_PAYMENT, ORDER_STATUS_PAYMENT_VERIFICATION -> "#C46A16" to "#FFF0DE"
            ORDER_STATUS_SHIPPING, ORDER_STATUS_CONFIRMED, ORDER_STATUS_READY_PICKUP -> "#128A35" to "#E8F7EC"
            ORDER_STATUS_PROCESSING -> "#9C6843" to "#F7E6DA"
            else -> "#9C6843" to "#F7E6DA"
        }
        val badgeBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = tvStatusPesanan.resources.displayMetrics.density * 20
            setColor(Color.parseColor(backgroundColor))
        }

        tvStatusPesanan.setTextColor(Color.parseColor(textColor))
        tvStatusPesanan.background = badgeBackground
    }

    private fun buildDateText(date: String, time: String): String {
        val cleanDate = date.ifBlank { "-" }
        return if (time.isBlank()) cleanDate else "$cleanDate, $time"
    }

    private fun buildItemSummary(order: Order): String {
        val itemTypeCount = order.cart.size
        val quantityCount = order.cart.sumOf { it.jumlahPesanan }
        return "$itemTypeCount jenis produk | $quantityCount item"
    }

    private fun buildAddressText(order: Order): String {
        val method = order.metodePengambilan.ifBlank { "Pesanan" }
        val address = order.user.alamat.orEmpty().ifBlank { "Alamat belum tersedia" }
        return if (method.contains("ambil", ignoreCase = true)) {
            "Diambil di ${pickupBranchNameForOrder(order)}"
        } else {
            address
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): HomeOrderUserAdapter.HomeUserViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = RvOrderUserBinding.inflate(inflater, parent, false)
        return HomeUserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HomeUserViewHolder, position: Int) {
        val cart = getItem(position)
        holder.bind(cart)
    }

    private class DiffCallback : DiffUtil.ItemCallback<Order>() {
        override fun areItemsTheSame(oldItem: Order, newItem: Order): Boolean {
            return oldItem.orderId == newItem.orderId
        }

        override fun areContentsTheSame(oldItem: Order, newItem: Order): Boolean {
            return oldItem == newItem
        }
    }

    private fun formatAndDisplayCurrency(value: String): String {
        // Tandai apakah angka negatif
        val isNegative = value.startsWith("-")
        val cleanValue = if (isNegative) value.substring(1) else value

        // Format ulang angka dengan menambahkan titik setiap 3 angka
        val stringBuilder = StringBuilder(cleanValue)
        val length = stringBuilder.length
        var i = length - 3
        while (i > 0) {
            stringBuilder.insert(i, ".")
            i -= 3
        }

        // Tambahkan tanda minus kembali jika angka negatif
        val formattedText = if (isNegative) {
            stringBuilder.insert(0, "-").toString()
        } else {
            stringBuilder.toString()
        }

        return formattedText
    }

}
