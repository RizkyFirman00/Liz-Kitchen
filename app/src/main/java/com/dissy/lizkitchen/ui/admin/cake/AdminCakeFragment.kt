package com.dissy.lizkitchen.ui.admin.cake

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.dissy.lizkitchen.utility.cakeFromMap
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class AdminCakeFragment : Fragment() {
    private var _binding: FragmentAdminCakeBinding? = null
    private val binding get() = _binding!!
    private val db = Firebase.firestore
    private val cakesCollection = db.collection("cakes")
    private lateinit var adminCakeAdapter: HomeAdminCakeAdapter
    private var cakeList = mutableListOf<Cake>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminCakeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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

        adminCakeAdapter = HomeAdminCakeAdapter {
            navigateToDetailDataActivity(it)
        }
        binding.rvAdmin.adapter = adminCakeAdapter
        binding.rvAdmin.layoutManager = LinearLayoutManager(requireContext())
        fetchDataAndUpdateRecyclerView()

        binding.searhView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText != null && newText.isNotEmpty()) {
                    adminCakeAdapter.filter.filter(newText)
                } else {
                    adminCakeAdapter.submitList(cakeList)
                }
                return true
            }
        })
    }

    private fun fetchDataAndUpdateRecyclerView() {
        cakesCollection.get()
            .addOnSuccessListener { result ->
                cakeList.clear()
                for (document in result) {
                    val cakeData = cakeFromMap(document.id, document.data)
                    cakeList.add(cakeData)
                }
                adminCakeAdapter.submitList(cakeList)
                adminCakeAdapter.sortDataByName()
            }
            .addOnFailureListener { exception ->
                Log.e("AdminCakeFragment", "Error fetching data", exception)
            }
    }

    private fun navigateToDetailDataActivity(cakeId: String) {
        val bundle = Bundle().apply {
            putString("documentId", cakeId)
        }
        findNavController().navigate(R.id.navigation_admin_cake_detail, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
