package com.dissy.lizkitchen.adapter.user

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.dissy.lizkitchen.adapter.admin.CartDetailUserAdapter
import com.dissy.lizkitchen.databinding.RvCheckoutBinding
import com.dissy.lizkitchen.model.Cart
import com.dissy.lizkitchen.utility.displayNameWithCategory
import com.dissy.lizkitchen.utility.displayUnit

class CheckoutUserAdapter() :
    ListAdapter<Cart, CheckoutUserAdapter.CheckoutUserViewHolder>(
        DiffCallback()
    ) {
    inner class CheckoutUserViewHolder(private val binding: RvCheckoutBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(cart: Cart) {
            binding.apply {
                tvCakeName.text = cart.cake.displayNameWithCategory()
                tvPrice.text = cart.cake.harga
                tvUnit.text = "/${cart.cake.displayUnit()}"
                tvJumlahPesanan.text = cart.jumlahPesanan.toString()
                Glide.with(itemView.context)
                    .load(cart.cake.imageUrl)
                    .into(ivCakeImage)
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): CheckoutUserAdapter.CheckoutUserViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = RvCheckoutBinding.inflate(inflater, parent, false)
        return CheckoutUserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CheckoutUserViewHolder, position: Int) {
        val order = getItem(position)
        holder.bind(order)
    }

    private class DiffCallback : DiffUtil.ItemCallback<Cart>() {
        override fun areItemsTheSame(oldItem: Cart, newItem: Cart): Boolean {
            return oldItem.cake.namaKue == newItem.cake.namaKue
        }

        override fun areContentsTheSame(oldItem: Cart, newItem: Cart): Boolean {
            return oldItem == newItem
        }
    }

}
