package com.dissy.lizkitchen.adapter.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.dissy.lizkitchen.databinding.RvCakeBinding
import com.dissy.lizkitchen.model.Cake
import java.util.Locale
import java.util.Locale.getDefault

class HomeAdminCakeAdapter(private val onItemClick: (String) -> Unit) :
    ListAdapter<Cake, HomeAdminCakeAdapter.HomeAdminViewHolder>(
        DiffCallback()
    ), Filterable {

    private var originalCakeList: List<Cake> = ArrayList()
    private var filteredCakeList: List<Cake> = ArrayList()

    init {
        originalCakeList = currentList
        filteredCakeList = originalCakeList
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HomeAdminViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = RvCakeBinding.inflate(inflater, parent, false)
        return HomeAdminViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HomeAdminViewHolder, position: Int) {
        val indekos = getItem(position)
        holder.bind(indekos, onItemClick)
    }

    override fun submitList(list: List<Cake>?) {
        super.submitList(list)
        originalCakeList = list ?: emptyList()
        filteredCakeList = originalCakeList
    }

    inner class HomeAdminViewHolder(
        private val binding: RvCakeBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(cake: Cake, onItemClick: (String) -> Unit) {
            binding.apply {
                tvCakeName.text = cake.namaKue
                tvPrice.text = cake.harga
                tvStok.text = cake.stok.toString()
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

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val filterPattern = constraint.toString().trim().lowercase(getDefault())
                val filteredList: List<Cake> = if (constraint.isNullOrEmpty()) {
                    originalCakeList
                } else {
                    originalCakeList.filter { cake ->
                        cake.namaKue.lowercase().contains(filterPattern) ||
                                cake.documentId.lowercase().contains(filterPattern)
                    }
                }

                val results = FilterResults()
                results.values = filteredList

                return results
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredCakeList = results?.values as List<Cake>
                submitList(filteredCakeList)
            }
        }
    }
}