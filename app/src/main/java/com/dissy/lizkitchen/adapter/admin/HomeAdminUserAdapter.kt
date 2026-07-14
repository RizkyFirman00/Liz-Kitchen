package com.dissy.lizkitchen.adapter.admin

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import androidx.recyclerview.widget.ListAdapter
import com.dissy.lizkitchen.model.Order
import com.dissy.lizkitchen.utility.ORDER_STATUS_CANCELED
import com.dissy.lizkitchen.utility.ORDER_STATUS_CONFIRMED
import com.dissy.lizkitchen.utility.ORDER_STATUS_DONE
import com.dissy.lizkitchen.utility.ORDER_STATUS_EXPIRED
import com.dissy.lizkitchen.utility.ORDER_STATUS_PENDING_PAYMENT
import com.dissy.lizkitchen.utility.ORDER_STATUS_PROCESSING
import com.dissy.lizkitchen.utility.ORDER_STATUS_READY_PICKUP
import com.dissy.lizkitchen.utility.ORDER_STATUS_SHIPPING
import com.dissy.lizkitchen.utility.metodePengambilanDisplayForOrder

class HomeAdminUserAdapter(private val onItemClick: (String) -> Unit) :
    ListAdapter<Order, HomeAdminUserAdapter.HomeAdminViewHolder>(
        DiffCallback()
    ), Filterable {

    private var orderListFull: List<Order> = ArrayList()
    private var originalOrderList: List<Order> = ArrayList()

    init {
        orderListFull = currentList
        originalOrderList = ArrayList(currentList)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): HomeAdminViewHolder {
        val inflater = android.view.LayoutInflater.from(parent.context)
        val binding =
            com.dissy.lizkitchen.databinding.RvUserBinding.inflate(inflater, parent, false)
        return HomeAdminViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HomeAdminViewHolder, position: Int) {
        val order = getItem(position)
        holder.bind(order, onItemClick)
    }

    override fun submitList(list: List<Order>?) {
        super.submitList(list)
        orderListFull = list ?: emptyList()
    }

    inner class HomeAdminViewHolder(private val binding: com.dissy.lizkitchen.databinding.RvUserBinding) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

        fun bind(order: Order, onItemClick: (String) -> Unit) {
            binding.apply {
                val statusText = order.status.ifBlank { "Status belum tersedia" }
                tvOrderId.text = order.orderId.ifBlank { "-" }
                tvOrderStatus.text = statusText
                applyStatusStyle(tvOrderStatus, statusText)
                tvName.text = order.user.name.orEmpty().ifBlank { "Pelanggan" }
                tvPhoneNumber.text = order.user.phoneNumber.orEmpty().ifBlank { "Nomor HP belum tersedia" }
                tvMetodePengambilan.text = metodePengambilanDisplayForOrder(order).ifBlank { "Metode belum dipilih" }
                tvTanggalOrder.text = buildDateText(order.tanggalOrder, order.jamOrder)
                tvItemSummary.text = buildItemSummary(order)
                tvTotalHarga.text = formatCurrency(order.totalPrice)
                root.setOnClickListener {
                    onItemClick.invoke(order.orderId)
                }
            }
        }
    }

    private fun applyStatusStyle(textView: TextView, status: String) {
        val (textColor, backgroundColor) = when (status) {
            ORDER_STATUS_DONE -> "#128A35" to "#E8F7EC"
            ORDER_STATUS_CANCELED, ORDER_STATUS_EXPIRED -> "#C62828" to "#FDECEC"
            ORDER_STATUS_PENDING_PAYMENT -> "#C46A16" to "#FFF0DE"
            ORDER_STATUS_SHIPPING, ORDER_STATUS_CONFIRMED, ORDER_STATUS_READY_PICKUP -> "#128A35" to "#E8F7EC"
            ORDER_STATUS_PROCESSING -> "#9C6843" to "#F7E6DA"
            else -> "#9C6843" to "#F7E6DA"
        }

        textView.setTextColor(Color.parseColor(textColor))
        textView.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = textView.resources.displayMetrics.density * 20
            setColor(Color.parseColor(backgroundColor))
        }
    }

    private fun buildDateText(date: String, time: String): String {
        val cleanDate = date.ifBlank { "Tanggal belum tersedia" }
        return if (time.isBlank()) cleanDate else "$cleanDate, $time"
    }

    private fun buildItemSummary(order: Order): String {
        val itemTypeCount = order.cart.size
        val quantityCount = order.cart.sumOf { it.jumlahPesanan }
        return "$itemTypeCount jenis produk | $quantityCount item"
    }

    private fun formatCurrency(value: Long): String {
        val formatted = StringBuilder(value.toString())
        var index = formatted.length - 3
        while (index > 0) {
            formatted.insert(index, ".")
            index -= 3
        }
        return "Rp $formatted"
    }

    private class DiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<Order>() {
        override fun areItemsTheSame(oldItem: Order, newItem: Order): Boolean {
            return oldItem.orderId == newItem.orderId
        }

        override fun areContentsTheSame(oldItem: Order, newItem: Order): Boolean {
            return oldItem == newItem
        }
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val filteredList: MutableList<Order> = ArrayList()

                if (constraint.isNullOrEmpty()) {
                    filteredList.addAll(orderListFull)
                } else {
                    val filterPattern = constraint.toString().trim()

                    for (order in orderListFull) {
                        if (order.user.name?.contains(filterPattern, ignoreCase = true) == true
                            || order.orderId.contains(filterPattern)
                        ) {
                            filteredList.add(order)
                        }
                    }
                }

                val results = FilterResults()
                results.values = filteredList

                return results
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                submitList(results?.values as List<Order>)
            }
        }
    }
}
