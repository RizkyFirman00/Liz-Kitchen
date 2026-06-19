package com.dissy.lizkitchen.adapter.user

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.dissy.lizkitchen.R
import com.dissy.lizkitchen.databinding.RvHomeBinding
import com.dissy.lizkitchen.model.Cake
import com.dissy.lizkitchen.model.ProductCategory
import com.dissy.lizkitchen.utility.availableCategories
import com.dissy.lizkitchen.utility.displayUnit
import com.dissy.lizkitchen.utility.normalizeProductUnit
import com.dissy.lizkitchen.utility.primaryCategory

class HomeUserAdapter(private val onItemClick: (String) -> Unit) :
    androidx.recyclerview.widget.ListAdapter<Cake, HomeUserAdapter.HomeUserViewHolder>(
        DiffCallback()
    ) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HomeUserViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = RvHomeBinding.inflate(inflater, parent, false)
        return HomeUserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HomeUserViewHolder, position: Int) {
        val indekos = getItem(position)
        holder.bind(indekos, onItemClick)
    }

    override fun onViewRecycled(holder: HomeUserViewHolder) {
        holder.stopVariantStockTicker()
        super.onViewRecycled(holder)
    }

    inner class HomeUserViewHolder(
        private val binding: RvHomeBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        private val handler = Handler(Looper.getMainLooper())
        private var stockTickerRunnable: Runnable? = null
        private var currentStockIndex = 0

        fun bind(cake: Cake, onItemClick: (String) -> Unit) {
            stopVariantStockTicker()
            val primaryCategory = cake.primaryCategory()
            val categories = cake.availableCategories()
            binding.apply {
                tvCakeName.text = cake.namaKue
                tvPrice.text = "Rp ${primaryCategory.harga}"
                tvUnit.text = " / ${normalizeProductUnit(primaryCategory.satuan)}"
                tvVariantSummary.text = buildVariantSummary(cake)
                tvVarianCount.text = "${categories.size} varian"
                startVariantStockTicker(categories)

                Glide.with(itemView.context)
                    .load(cake.imageUrl)
                    .placeholder(R.drawable.null_image)
                    .error(R.drawable.null_image)
                    .into(ivCakeBanner)
                root.setOnClickListener {
                    onItemClick.invoke(cake.documentId)
                }
            }
        }

        fun stopVariantStockTicker() {
            stockTickerRunnable?.let { handler.removeCallbacks(it) }
            stockTickerRunnable = null
        }

        private fun startVariantStockTicker(categories: List<ProductCategory>) {
            currentStockIndex = 0
            updateVariantStockText(categories)

            if (categories.size <= 1) return

            stockTickerRunnable = object : Runnable {
                override fun run() {
                    currentStockIndex = (currentStockIndex + 1) % categories.size
                    updateVariantStockText(categories)
                    handler.postDelayed(this, STOCK_TICKER_INTERVAL_MS)
                }
            }
            handler.postDelayed(stockTickerRunnable!!, STOCK_TICKER_INTERVAL_MS)
        }

        private fun updateVariantStockText(categories: List<ProductCategory>) {
            val category = categories.getOrNull(currentStockIndex) ?: return
            val normalizedVariantName = category.namaKategori.trim()
            val variantName = if (
                normalizedVariantName.isBlank() ||
                normalizedVariantName.equals("Default", ignoreCase = true)
            ) {
                "utama"
            } else {
                normalizedVariantName
            }
            val isAvailable = category.stok > 0

            binding.tvStok.text = "Stok $variantName ${category.stok}"
            binding.tvStok.setTextColor(
                ContextCompat.getColor(itemView.context, if (isAvailable) R.color.green else R.color.red)
            )
        }

        private fun buildVariantSummary(cake: Cake): String {
            return cake.availableCategories()
                .map { it.namaKategori.ifBlank { it.displayUnit() } }
                .distinct()
                .take(3)
                .joinToString(", ")
                .ifBlank { "Varian tersedia" }
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

    private companion object {
        const val STOCK_TICKER_INTERVAL_MS = 2_400L
    }
}
