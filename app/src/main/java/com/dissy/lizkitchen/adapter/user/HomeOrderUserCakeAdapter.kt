package com.dissy.lizkitchen.adapter.user

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dissy.lizkitchen.databinding.RvOrderCakeUserBinding
import com.dissy.lizkitchen.model.Cart
import com.dissy.lizkitchen.utility.displayNameWithCategory

class HomeOrderUserCakeAdapter : ListAdapter<Cart, HomeOrderUserCakeAdapter.HomeOrderUserCakeViewHolder>(
    DiffCallback()
) {

    inner class HomeOrderUserCakeViewHolder(
        private val binding: RvOrderCakeUserBinding,
    ): RecyclerView.ViewHolder(binding.root) {

            fun bind(cart: Cart) {
                binding.apply {
                    tvCakeName.text = cart.cake.displayNameWithCategory()
                    tvJumlahPesanan.text = cart.jumlahPesanan.toString()
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

}
