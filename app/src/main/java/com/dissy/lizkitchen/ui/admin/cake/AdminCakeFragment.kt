package com.dissy.lizkitchen.ui.admin.cake

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.dissy.lizkitchen.R
import com.dissy.lizkitchen.adapter.admin.HomeAdminCakeAdapter
import com.dissy.lizkitchen.databinding.FragmentAdminCakeBinding
import com.dissy.lizkitchen.model.Cake
import com.dissy.lizkitchen.ui.login.LoginActivity
import com.dissy.lizkitchen.utility.Preferences
import com.dissy.lizkitchen.utility.availableCategories
import com.dissy.lizkitchen.utility.cakeFromMap
import com.dissy.lizkitchen.utility.clearFocusWhenTouchOutsideInput
import com.dissy.lizkitchen.utility.primaryCategory
import com.dissy.lizkitchen.utility.setFirebaseRequestLoading
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class AdminCakeFragment : Fragment() {
    private var _binding: FragmentAdminCakeBinding? = null
    private val binding get() = _binding!!
    private val db = Firebase.firestore
    private val cakesCollection = db.collection("cakes")
    private lateinit var adminCakeAdapter: HomeAdminCakeAdapter
    private var cakeList = mutableListOf<Cake>()
    private var searchQuery = ""
    private var selectedSort = SORT_NAME_AZ
    private var selectedVarianFilter = FILTER_ALL

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminCakeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.root.clearFocusWhenTouchOutsideInput()
        
        // Sesuaikan warna status bar agar senada dengan background coklat tua
        requireActivity().window.statusBarColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.brown_old)

        binding.btnToLogout.setOnClickListener {
            Preferences.logout(requireContext())
            Intent(requireContext(), LoginActivity::class.java).also {
                Toast.makeText(requireContext(), "Berhasil Logout", Toast.LENGTH_SHORT).show()
                startActivity(it)
                requireActivity().finish()
            }
        }

        binding.btnAddData.setOnClickListener {
            findNavController().navigate(R.id.navigation_admin_add_cake)
        }

        binding.btnToHome.setOnClickListener {
            findNavController().navigateUp()
        }

        adminCakeAdapter = HomeAdminCakeAdapter { navigateToDetailDataActivity(it) }
        binding.rvAdmin.adapter = adminCakeAdapter
        binding.rvAdmin.layoutManager = LinearLayoutManager(requireContext())

        setupSearch()
        setupFilterSort()
        fetchDataAndUpdateRecyclerView()
    }

    private fun setupSearch() {
        binding.searhView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchQuery = query.orEmpty()
                applyFilterAndSort()
                binding.searhView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                searchQuery = newText.orEmpty()
                applyFilterAndSort()
                return true
            }
        })
    }

    private fun setupFilterSort() {
        // Sort spinner
        val sortAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            SORT_OPTIONS
        )
        binding.spinnerSortOrder.adapter = sortAdapter
        binding.spinnerSortOrder.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedSort = SORT_OPTIONS[position]
                applyFilterAndSort()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        // Varian count filter spinner
        val varianAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            VARIAN_FILTER_OPTIONS
        )
        binding.spinnerFilterVarian.adapter = varianAdapter
        binding.spinnerFilterVarian.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedVarianFilter = VARIAN_FILTER_OPTIONS[position]
                applyFilterAndSort()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun applyFilterAndSort() {
        val query = searchQuery.trim().lowercase()

        var result = cakeList.filter { cake ->
            val matchSearch = query.isEmpty() ||
                    cake.namaKue.lowercase().contains(query) ||
                    cake.availableCategories().any { it.namaKategori.lowercase().contains(query) }

            val matchVarian = when (selectedVarianFilter) {
                "1 Varian" -> cake.availableCategories().size == 1
                "2 Varian" -> cake.availableCategories().size == 2
                "3+ Varian" -> cake.availableCategories().size >= 3
                else -> true
            }

            matchSearch && matchVarian
        }

        result = when (selectedSort) {
            SORT_NAME_ZA -> result.sortedByDescending { it.namaKue.lowercase() }
            SORT_PRICE_LOW -> result.sortedBy { cake ->
                cake.primaryCategory().harga.filter { it.isDigit() }.toLongOrNull() ?: 0
            }
            SORT_PRICE_HIGH -> result.sortedByDescending { cake ->
                cake.primaryCategory().harga.filter { it.isDigit() }.toLongOrNull() ?: 0
            }
            SORT_STOCK_HIGH -> result.sortedByDescending { cake ->
                cake.availableCategories().sumOf { it.stok }
            }
            SORT_VARIAN_HIGH -> result.sortedByDescending { it.availableCategories().size }
            else -> result.sortedBy { it.namaKue.lowercase() }
        }

        adminCakeAdapter.submitList(result)
    }

    private fun fetchDataAndUpdateRecyclerView() {
        binding.root.setFirebaseRequestLoading(true, binding.progressBar2)
        cakesCollection.get()
            .addOnSuccessListener { result ->
                if (_binding == null) return@addOnSuccessListener
                cakeList.clear()
                for (document in result) {
                    val cakeData = cakeFromMap(document.id, document.data)
                    cakeList.add(cakeData)
                }
                applyFilterAndSort()
                binding.root.setFirebaseRequestLoading(false, binding.progressBar2)
            }
            .addOnFailureListener { exception ->
                if (_binding != null) binding.root.setFirebaseRequestLoading(false, binding.progressBar2)
                Log.e("AdminCakeFragment", "Error fetching data", exception)
            }
    }

    private fun navigateToDetailDataActivity(cakeId: String) {
        val bundle = Bundle().apply { putString("documentId", cakeId) }
        findNavController().navigate(R.id.navigation_admin_cake_detail, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val FILTER_ALL = "Semua"
        private const val SORT_NAME_AZ = "Nama A-Z"
        private const val SORT_NAME_ZA = "Nama Z-A"
        private const val SORT_PRICE_LOW = "Harga Terendah"
        private const val SORT_PRICE_HIGH = "Harga Tertinggi"
        private const val SORT_STOCK_HIGH = "Stok Terbanyak"
        private const val SORT_VARIAN_HIGH = "Varian Terbanyak"

        private val SORT_OPTIONS = listOf(
            SORT_NAME_AZ,
            SORT_NAME_ZA,
            SORT_PRICE_LOW,
            SORT_PRICE_HIGH,
            SORT_STOCK_HIGH,
            SORT_VARIAN_HIGH
        )

        private val VARIAN_FILTER_OPTIONS = listOf(
            FILTER_ALL,
            "1 Varian",
            "2 Varian",
            "3+ Varian"
        )
    }
}
