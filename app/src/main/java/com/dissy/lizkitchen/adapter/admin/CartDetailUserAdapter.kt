package com.dissy.lizkitchen.adapter.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.dissy.lizkitchen.databinding.RvOrderDetailBinding
import com.dissy.lizkitchen.model.Cart
import com.dissy.lizkitchen.utility.displayNameWithCategory
import com.dissy.lizkitchen.utility.normalizeProductUnit
import com.dissy.lizkitchen.utility.productPriceToLong

class CartDetailUserAdapter :
    ListAdapter<Cart, CartDetailUserAdapter.CartDetailUserViewHolder>(
        DiffCallback()
    ) {
    inner class CartDetailUserViewHolder(private val binding: RvOrderDetailBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(cart: Cart) {
            binding.apply {
                val unit = normalizeProductUnit(cart.cake.satuan)
                val unitPrice = productPriceToLong(cart.cake.harga)
                tvCakeName.text = cart.cake.displayNameWithCategory()
                tvItemPrice.text = "Rp ${formatCurrency(unitPrice)} / $unit"
                tvJumlahPesanan.text = "${cart.jumlahPesanan} $unit"
                tvSubtotal.text = "Subtotal Rp ${formatCurrency(unitPrice * cart.jumlahPesanan)}"
                Glide.with(itemView.context)
                    .load(cart.cake.imageUrl)
                    .into(ivImageCake)
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): CartDetailUserAdapter.CartDetailUserViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = RvOrderDetailBinding.inflate(inflater, parent, false)
        return CartDetailUserViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: CartDetailUserViewHolder,
        position: Int
    ) {
        val order = getItem(position)
        holder.bind(order)
    }

    private class DiffCallback : DiffUtil.ItemCallback<Cart>() {
        override fun areItemsTheSame(oldItem: Cart, newItem: Cart): Boolean {
            return oldItem.cakeId == newItem.cakeId && oldItem.cake.kategori == newItem.cake.kategori
        }

        override fun areContentsTheSame(oldItem: Cart, newItem: Cart): Boolean {
            return oldItem == newItem
        }
    }

    private fun formatCurrency(value: Long): String {
        val formatted = StringBuilder(value.toString())
        var index = formatted.length - 3
        while (index > 0) {
            formatted.insert(index, ".")
            index -= 3
        }
        return formatted.toString()
    }
}
