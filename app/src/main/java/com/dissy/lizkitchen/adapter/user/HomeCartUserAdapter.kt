package com.dissy.lizkitchen.adapter.user

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.dissy.lizkitchen.R
import com.dissy.lizkitchen.databinding.RvCartBinding
import com.dissy.lizkitchen.model.Cart
import com.dissy.lizkitchen.utility.displayNameWithCategory
import com.dissy.lizkitchen.utility.normalizeProductUnit
import com.dissy.lizkitchen.utility.productPriceToLong

class HomeCartUserAdapter(
    private val listener: CartInteractionListener,
    private val deleteListener: CartDeleteListener
) : ListAdapter<Cart, HomeCartUserAdapter.CartUserViewHolder>(
        DiffCallback()
    ) {

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
                val unitPrice = productPriceToLong(cart.cake.harga)
                val unit = normalizeProductUnit(cart.cake.satuan)

                tvCakeName.text = cart.cake.displayNameWithCategory()
                tvPrice.text = formatCurrency(unitPrice)
                tvUnit.text = " / $unit"
                tvStock.text = "Stok ${cart.cake.stok} $unit"
                bindQuantity(cart, unitPrice)

                Glide.with(itemView.context)
                    .load(cart.cake.imageUrl)
                    .placeholder(R.drawable.null_image)
                    .error(R.drawable.null_image)
                    .into(ivCakeBanner)

                btnDelete.setOnClickListener {
                    showDeleteConfirmationDialog(cart)
                }
                btnPlus.setOnClickListener {
                    if (cart.jumlahPesanan >= cart.cake.stok) {
                        Toast.makeText(itemView.context, "Stok tidak mencukupi, Stok = ${cart.cake.stok}", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    cart.jumlahPesanan++
                    bindQuantity(cart, unitPrice)
                    listener.onQuantityChanged(cart, cart.jumlahPesanan)
                }
                btnMinus.setOnClickListener {
                    if (cart.jumlahPesanan > 1) {
                        cart.jumlahPesanan--
                        bindQuantity(cart, unitPrice)
                        listener.onQuantityChanged(cart, cart.jumlahPesanan)
                    } else {
                        showDeleteConfirmationDialog(cart)
                    }
                }
            }
        }

        private fun RvCartBinding.bindQuantity(cart: Cart, unitPrice: Long) {
            tvJmlh.text = cart.jumlahPesanan.toString()
            tvSubtotal.text = "Rp ${formatCurrency(unitPrice * cart.jumlahPesanan)}"
            btnPlus.alpha = if (cart.jumlahPesanan >= cart.cake.stok) 0.45f else 1f
        }

        private fun showDeleteConfirmationDialog(cart: Cart) {
            AlertDialog.Builder(itemView.context)
                .setTitle("Hapus Produk")
                .setMessage("Hapus ${cart.cake.displayNameWithCategory()} dari keranjang?")
                .setPositiveButton("Hapus") { _, _ ->
                    deleteListener.onCartItemDelete(cart)
                }
                .setNegativeButton("Batal", null)
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

    private fun formatCurrency(value: Long): String {
        val stringBuilder = StringBuilder(value.toString())
        var i = stringBuilder.length - 3
        while (i > 0) {
            stringBuilder.insert(i, ".")
            i -= 3
        }
        return stringBuilder.toString()
    }
}
