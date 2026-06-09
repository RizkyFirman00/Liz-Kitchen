package com.dissy.lizkitchen.adapter.user

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.dissy.lizkitchen.databinding.RvHomeBinding
import com.dissy.lizkitchen.model.Cake
import com.dissy.lizkitchen.utility.displayUnit
import com.dissy.lizkitchen.utility.primaryCategory

class HomeUserAdapter(private val onItemClick: (String) -> Unit) :
    androidx.recyclerview.widget.ListAdapter<Cake, HomeUserAdapter.HomeUserViewHolder>(
        DiffCallback()
    ) {
    private val cakesList = mutableListOf<String>()
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HomeUserViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = RvHomeBinding.inflate(inflater, parent, false)
        return HomeUserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HomeUserViewHolder, position: Int) {
        val indekos = getItem(position)
        holder.bind(indekos, onItemClick)
    }

    inner class HomeUserViewHolder(
        private val binding: RvHomeBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(cake: Cake, onItemClick: (String) -> Unit) {
            val primaryCategory = cake.primaryCategory()
            binding.apply {
                tvCakeName.text = cake.namaKue
                tvPrice.text = primaryCategory.harga
                tvUnit.text = "/${primaryCategory.displayUnit()}"
                tvStock.text = primaryCategory.stok.toString()
                Glide.with(itemView.context)
                    .load(cake.imageUrl)
                    .into(ivCakeBanner)
                root.setOnClickListener {
                    onItemClick.invoke(cake.documentId)
                }
            }
        }
    }

    fun sortDataByName() {
        submitList(currentList.sortedBy { it.namaKue })
    }

    private class DiffCallback : DiffUtil.ItemCallback<Cake>() {
        override fun areItemsTheSame(oldItem: Cake, newItem: Cake): Boolean {
            return oldItem.namaKue == newItem.namaKue
        }

        override fun areContentsTheSame(oldItem: Cake, newItem: Cake): Boolean {
            return oldItem == newItem
        }
    }
}
