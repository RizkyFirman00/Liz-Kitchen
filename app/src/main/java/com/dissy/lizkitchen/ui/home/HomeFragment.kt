package com.dissy.lizkitchen.ui.home

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.dissy.lizkitchen.adapter.user.HomeUserAdapter
import com.dissy.lizkitchen.databinding.FragmentHomeBinding
import com.dissy.lizkitchen.model.Cake
import com.dissy.lizkitchen.ui.login.LoginActivity
import com.dissy.lizkitchen.ui.profile.ProfileActivity
import com.dissy.lizkitchen.utility.Preferences
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val db = Firebase.firestore
    private val cakesCollection = db.collection("cakes")
    private lateinit var userAdapter: HomeUserAdapter

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
        binding.rvUser.layoutManager = LinearLayoutManager(requireContext())
        fetchDataAndUpdateRecyclerView()

        binding.btnToProfile.setOnClickListener {
            Intent(requireContext(), ProfileActivity::class.java).also {
                startActivity(it)
            }
        }

        binding.btnToLogout.setOnClickListener {
            Preferences.logout(requireContext())
            Intent(requireContext(), LoginActivity::class.java).also {
                Toast.makeText(requireContext(), "Berhasil Logout", Toast.LENGTH_SHORT).show()
                startActivity(it)
                requireActivity().finish()
            }
        }
    }

    private fun fetchDataAndUpdateRecyclerView() {
        binding.progressBar2.visibility = View.VISIBLE
        cakesCollection.get()
            .addOnSuccessListener { result ->
                val cakesList = mutableListOf<Cake>()
                for (document in result) {
                    val cakeData = document.toObject(Cake::class.java)
                    cakesList.add(cakeData)
                }
                if (cakesList.isEmpty()) {
                    binding.tvEmptyData.visibility = View.VISIBLE
                } else {
                    binding.tvEmptyData.visibility = View.GONE
                }
                userAdapter.submitList(cakesList)
                userAdapter.sortDataByName()
                binding.progressBar2.visibility = View.GONE
            }
            .addOnFailureListener { exception ->
                Toast.makeText(requireContext(), "Error fetching data: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun navigateToDetailDataActivity(cakeId: String) {
        val intent = Intent(requireContext(), CakeDetailUserActivity::class.java)
        intent.putExtra("cakeId", cakeId)
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}