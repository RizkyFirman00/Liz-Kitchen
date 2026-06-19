package com.dissy.lizkitchen.adapter.user

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.dissy.lizkitchen.R
import com.dissy.lizkitchen.databinding.RvOrderCakeUserBinding
import com.dissy.lizkitchen.model.Cart
import com.dissy.lizkitchen.utility.displayNameWithCategory
import com.dissy.lizkitchen.utility.normalizeProductUnit

class HomeOrderUserCakeAdapter : ListAdapter<Cart, HomeOrderUserCakeAdapter.HomeOrderUserCakeViewHolder>(
    DiffCallback()
) {

    inner class HomeOrderUserCakeViewHolder(
        private val binding: RvOrderCakeUserBinding,
    ): RecyclerView.ViewHolder(binding.root) {

            fun bind(cart: Cart) {
                binding.apply {
                    val unit = normalizeProductUnit(cart.cake.satuan)
                    tvCakeName.text = cart.cake.displayNameWithCategory()
                    tvItemPrice.text = "Rp ${formatCurrency(cart.cake.harga)} / $unit"
                    tvJumlahPesanan.text = "x${cart.jumlahPesanan}"
                    Glide.with(itemView.context)
                        .load(cart.cake.imageUrl)
                        .placeholder(R.drawable.null_image)
                        .error(R.drawable.null_image)
                        .into(ivImageCake)
                }
            }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): HomeOrderUserCakeAdapter.HomeOrderUserCakeViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = RvOrderCakeUserBinding.inflate(inflater, parent, false)
        return HomeOrderUserCakeViewHolder(binding)
    }
    override fun onBindViewHolder(
        holder: HomeOrderUserCakeAdapter.HomeOrderUserCakeViewHolder,
        position: Int
    ) {
        val cart = getItem(position)
        holder.bind(cart)
    }

    private class DiffCallback : DiffUtil.ItemCallback<Cart>() {
        override fun areItemsTheSame(oldItem: Cart, newItem: Cart): Boolean {
            return oldItem.cakeId  == newItem.cakeId
        }

        override fun areContentsTheSame(oldItem: Cart, newItem: Cart): Boolean {
            return oldItem == newItem
        }
    }

    private fun formatCurrency(value: String): String {
        val digits = value.filter { it.isDigit() }
        if (digits.isBlank()) return value.ifBlank { "0" }

        val stringBuilder = StringBuilder(digits)
        var i = stringBuilder.length - 3
        while (i > 0) {
            stringBuilder.insert(i, ".")
            i -= 3
        }
        return stringBuilder.toString()
    }
}
