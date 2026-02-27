package com.dissy.lizkitchen.adapter.admin

import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.recyclerview.widget.ListAdapter
import com.dissy.lizkitchen.model.Order

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
            if (order.status == "Selesai") {
                binding.tvOrderStatus.setTextColor(android.graphics.Color.parseColor("#0ACB12"))
            } else if (order.status == "Dibatalkan") {
                binding.tvOrderStatus.setTextColor(android.graphics.Color.parseColor("#D10826"))
            } else if (order.status == "Menunggu Pembayaran") {
                binding.tvOrderStatus.setTextColor(android.graphics.Color.parseColor("#D10826"))
            } else if (order.status == "Sedang Dikirim") {
                binding.tvOrderStatus.setTextColor(android.graphics.Color.parseColor("#0ACB12"))
            } else if (order.status == "Sedang Diproses") {
                binding.tvOrderStatus.setTextColor(android.graphics.Color.parseColor("#9C6843"))
            } else if (order.status == "Sudah Dikonfirmasi") {
                binding.tvOrderStatus.setTextColor(android.graphics.Color.parseColor("#0ACB12"))
            }

            binding.apply {
                tvOrderId.text = order.orderId
                tvOrderStatus.text = order.status
                tvUsername.text = order.user.username
                tvPhoneNumber.text = order.user.phoneNumber
                root.setOnClickListener {
                    onItemClick.invoke(order.orderId)
                }
            }
        }
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
                        if (order.user.username?.contains(filterPattern) == true
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