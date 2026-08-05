package com.dissy.lizkitchen.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.dissy.lizkitchen.adapter.user.HomeUserAdapter
import com.dissy.lizkitchen.databinding.FragmentHomeBinding
import com.dissy.lizkitchen.model.Cake
import com.dissy.lizkitchen.ui.login.LoginActivity
import com.dissy.lizkitchen.utility.Preferences
import com.dissy.lizkitchen.utility.availableCategories
import com.dissy.lizkitchen.utility.cakeFromMap
import com.dissy.lizkitchen.utility.expiryAtMillis
import com.dissy.lizkitchen.utility.isExpired
import com.dissy.lizkitchen.utility.isExpiringSoon
import com.dissy.lizkitchen.utility.primaryCategory
import com.dissy.lizkitchen.utility.setFirebaseRequestLoading
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

import androidx.navigation.fragment.findNavController
import com.dissy.lizkitchen.R

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val db = Firebase.firestore
    private val cakesCollection = db.collection("cakes")
    private lateinit var userAdapter: HomeUserAdapter
    private var originalList = listOf<Cake>()
    private var searchQuery = ""
    private var selectedExpiryFilter = EXPIRY_ALL

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        userAdapter = HomeUserAdapter {
            navigateToDetailDataActivity(it)
        }
        binding.rvUser.adapter = userAdapter
        binding.rvUser.layoutManager = StaggeredGridLayoutManager(
            2,
            StaggeredGridLayoutManager.VERTICAL
        )
        binding.rvUser.itemAnimator = null
        binding.rvUser.setHasFixedSize(false)
        binding.tvHomeSummary.text = "Memuat katalog..."
        setupFilters()
        fetchDataAndUpdateRecyclerView()

        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.btn_toProfile -> {
                    findNavController().navigate(R.id.navigation_profile)
                    true
                }
                R.id.btn_toLogout -> {
                    Preferences.logout(requireContext())
                    Intent(requireContext(), LoginActivity::class.java).also {
                        Toast.makeText(requireContext(), "Berhasil Logout", Toast.LENGTH_SHORT).show()
                        startActivity(it)
                        requireActivity().finish()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupFilters() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchQuery = query.orEmpty()
                applyFilterAndSort(scrollToTop = true)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                searchQuery = newText.orEmpty()
                applyFilterAndSort(scrollToTop = true)
                return true
            }
        })

        binding.chipGroupSort.setOnCheckedChangeListener { _, checkedId ->
            applyFilterAndSort(scrollToTop = true)
        }

        binding.chipGroupExpiry.setOnCheckedChangeListener { _, checkedId ->
            selectedExpiryFilter = when (checkedId) {
                R.id.chipExpiringSoon -> EXPIRY_SOON
                R.id.chipExpired -> EXPIRY_EXPIRED
                R.id.chipExpiryNotSet -> EXPIRY_NOT_SET
                else -> EXPIRY_ALL
            }
            applyFilterAndSort(scrollToTop = true)
        }

        binding.btnResetFilter.setOnClickListener {
            binding.searchView.setQuery("", false)
            binding.searchView.clearFocus()
            binding.chipGroupSort.clearCheck()
            binding.chipGroupExpiry.clearCheck()
            searchQuery = ""
            selectedExpiryFilter = EXPIRY_ALL
            applyFilterAndSort(scrollToTop = true)
        }
    }

    private fun applyFilterAndSort(scrollToTop: Boolean = false) {
        var filteredList = originalList.filter {
            it.matchesSearchQuery(searchQuery) && it.matchesExpiryFilter(selectedExpiryFilter)
        }

        val expiredCount = filteredList.count { it.isExpired() }

        filteredList = when (binding.chipGroupSort.checkedChipId) {
            R.id.chipNameAZ -> filteredList.sortedBy { it.namaKue.lowercase() }
            R.id.chipPriceLow -> filteredList.sortedBy { it.primaryCategory().harga.filter { c -> c.isDigit() }.toLongOrNull() ?: 0 }
            R.id.chipPriceHigh -> filteredList.sortedByDescending { it.primaryCategory().harga.filter { c -> c.isDigit() }.toLongOrNull() ?: 0 }
            R.id.chipStock -> filteredList.sortedByDescending { it.primaryCategory().stok }
            else -> filteredList
        }

        userAdapter.submitList(filteredList) {
            if (scrollToTop && _binding != null) {
                scrollProductListToTop()
            }
        }
        binding.tvHomeSummary.text = buildHomeSummary(filteredList, expiredCount)
        binding.tvEmptyData.text = when (selectedExpiryFilter) {
            EXPIRY_SOON -> "Tidak ada kue yang segera expired"
            EXPIRY_EXPIRED -> "Tidak ada kue yang sudah expired"
            EXPIRY_NOT_SET -> "Semua kue sudah memiliki pengaturan expired"
            else -> "Produk tidak ditemukan"
        }
        binding.tvEmptyData.visibility = if (filteredList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun scrollProductListToTop() {
        binding.rvUser.post {
            val layoutManager = binding.rvUser.layoutManager as? StaggeredGridLayoutManager
            layoutManager?.scrollToPositionWithOffset(0, 0) ?: binding.rvUser.scrollToPosition(0)
        }
    }

    private fun Cake.matchesSearchQuery(query: String): Boolean {
        if (query.isBlank()) return true

        return namaKue.contains(query, ignoreCase = true) ||
            availableCategories().any { category ->
                category.namaKategori.contains(query, ignoreCase = true)
            }
    }

    private fun Cake.matchesExpiryFilter(filter: String): Boolean {
        return when (filter) {
            EXPIRY_SOON -> isExpiringSoon()
            EXPIRY_EXPIRED -> isExpired()
            EXPIRY_NOT_SET -> expiryAtMillis() == null
            else -> true
        }
    }

    private fun buildHomeSummary(cakes: List<Cake>, expiredCount: Int = 0): String {
        val availableCakes = cakes.filterNot { it.isExpired() }
        val productCount = availableCakes.size
        val variantCount = availableCakes.sumOf { it.availableCategories().size }
        return if (cakes.isEmpty()) {
            "Tidak ada produk sesuai pencarian."
        } else {
            buildString {
                append("$productCount produk tersedia | $variantCount varian")
                if (expiredCount > 0) {
                    append(" • $expiredCount produk expired")
                }
            }
        }
    }

    private fun fetchDataAndUpdateRecyclerView() {
        binding.root.setFirebaseRequestLoading(true, binding.progressBar2)
        cakesCollection.get()
            .addOnSuccessListener { result ->
                if (_binding == null) return@addOnSuccessListener

                val cakesList = mutableListOf<Cake>()
                for (document in result) {
                    val cakeData = cakeFromMap(document.id, document.data)
                    cakesList.add(cakeData)
                }
                if (cakesList.isEmpty()) {
                    binding.tvEmptyData.visibility = View.VISIBLE
                } else {
                    binding.tvEmptyData.visibility = View.GONE
                }
                originalList = cakesList
                applyFilterAndSort()
                binding.root.setFirebaseRequestLoading(false, binding.progressBar2)
            }
            .addOnFailureListener { exception ->
                if (_binding == null) return@addOnFailureListener
                binding.root.setFirebaseRequestLoading(false, binding.progressBar2)
                binding.tvHomeSummary.text = "Gagal memuat katalog."
                binding.tvEmptyData.visibility = View.VISIBLE
                Toast.makeText(requireContext(), "Error fetching data: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun navigateToDetailDataActivity(cakeId: String) {
        val bundle = Bundle().apply {
            putString("cakeId", cakeId)
        }
        findNavController().navigate(R.id.navigation_cake_detail, bundle)
    }

    private companion object {
        const val EXPIRY_ALL = "Semua"
        const val EXPIRY_SOON = "Segera expired"
        const val EXPIRY_EXPIRED = "Sudah expired"
        const val EXPIRY_NOT_SET = "Belum diatur"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
