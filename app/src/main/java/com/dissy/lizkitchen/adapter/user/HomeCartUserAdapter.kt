package com.dissy.lizkitchen.adapter.user

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.dissy.lizkitchen.databinding.RvCartBinding
import com.dissy.lizkitchen.model.Cart
import com.dissy.lizkitchen.utility.displayNameWithCategory
import com.dissy.lizkitchen.utility.displayUnit

class HomeCartUserAdapter(
    private val listener: CartInteractionListener,
    private val deleteListener: CartDeleteListener
) : ListAdapter<Cart, HomeCartUserAdapter.CartUserViewHolder>(
        DiffCallback()
    ) {
    private var totalHarga: Long = 0

    interface CartInteractionListener {
        fun onQuantityChanged(cart: Cart, newQuantity: Long)
    }

    interface CartDeleteListener {
        fun onCartItemDelete(cart: Cart)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartUserViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = RvCartBinding.inflate(inflater, parent, false)
        return CartUserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CartUserViewHolder, position: Int) {
        val cart = getItem(position)
        holder.bind(cart)
    }

    inner class CartUserViewHolder(
        private val binding: RvCartBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(cart: Cart) {
            binding.apply {
                tvCakeName.text = cart.cake.displayNameWithCategory()
                tvPrice.text = cart.cake.harga
                tvUnit.text = "/${cart.cake.displayUnit()}"
                tvJmlh.text = cart.jumlahPesanan.toString()
                Glide.with(itemView.context)
                    .load(cart.cake.imageUrl)
                    .into(ivCakeBanner)
                btnPlus.setOnClickListener {
                    if (cart.jumlahPesanan >= cart.cake.stok) {
                        Toast.makeText(itemView.context, "Stok tidak mencukupi, Stok = ${cart.cake.stok}", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    cart.jumlahPesanan++
                    tvJmlh.text = cart.jumlahPesanan.toString()
                    listener.onQuantityChanged(cart, cart.jumlahPesanan)
                }
                btnMinus.setOnClickListener {
                    if (cart.jumlahPesanan > 1) {
                        cart.jumlahPesanan--
                        tvJmlh.text = cart.jumlahPesanan.toString()
                        listener.onQuantityChanged(cart, cart.jumlahPesanan)
                    } else {
                        showDeleteConfirmationDialog(cart)
                    }
                }
            }
        }

        private fun showDeleteConfirmationDialog(cart: Cart) {
            AlertDialog.Builder(itemView.context)
                .setTitle("Konfirmasi")
                .setMessage("Apakah Anda yakin ingin menghapus item ini dari keranjang?")
                .setPositiveButton("Ya") { _, _ ->
                    deleteListener.onCartItemDelete(cart)
                }
                .setNegativeButton("Tidak", null)
                .show()
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<Cart>() {
        override fun areItemsTheSame(oldItem: Cart, newItem: Cart): Boolean {
            return oldItem.cakeId == newItem.cakeId
        }

        override fun areContentsTheSame(oldItem: Cart, newItem: Cart): Boolean {
            return oldItem == newItem
        }
    }
}
