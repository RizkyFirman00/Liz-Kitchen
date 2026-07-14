package com.dissy.lizkitchen.adapter.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dissy.lizkitchen.adapter.admin.ReportAdminAdapter.ReportAdminHolder
import com.dissy.lizkitchen.databinding.RvMutasiBinding
import com.dissy.lizkitchen.model.Order

class ReportAdminAdapter(): ListAdapter<Order, ReportAdminHolder>(
    DiffCallback()
) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ReportAdminHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = RvMutasiBinding.inflate(inflater, parent, false)
        return ReportAdminHolder(binding)
    }

    inner class ReportAdminHolder(private val binding: RvMutasiBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(order: Order) {
            binding.apply {
                tvIdOrder.text = order.orderId
                tvNohpOrder.text = order.user.phoneNumber
                tvStatusOrder.text = order.status
                tvTanggalOrder.text = order.tanggalOrder
                tvUserOrder.text = order.user.name
                tvTotalHargaOrder.text = formatAndDisplayCurrency(order.totalPrice.toString())
            }
        }

    }

    override fun onBindViewHolder(holder: ReportAdminHolder, position: Int) {
        val order = getItem(position)
        holder.bind(order)
    }

    private class DiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<Order>() {
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

        return "Rp. $formattedText"
    }

}
