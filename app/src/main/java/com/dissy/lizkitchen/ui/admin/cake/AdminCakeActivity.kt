package com.dissy.lizkitchen.ui.admin.cake

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import com.dissy.lizkitchen.adapter.admin.HomeAdminCakeAdapter
import com.dissy.lizkitchen.databinding.ActivityAdminBinding
import com.dissy.lizkitchen.model.Cake
import com.dissy.lizkitchen.ui.admin.AdminHomeActivity
import com.dissy.lizkitchen.ui.base.BaseActivity
import com.dissy.lizkitchen.ui.login.LoginActivity
import com.dissy.lizkitchen.utility.Preferences
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class AdminCakeActivity : BaseActivity() {
    private val db = Firebase.firestore
    private val cakesCollection = db.collection("cakes")
    private lateinit var adminCakeAdapter: HomeAdminCakeAdapter
    private var cakeList = mutableListOf<Cake>()
    private val binding by lazy { ActivityAdminBinding.inflate(layoutInflater) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.btnToLogout.setOnClickListener {
            Preferences.logout(this)
            Intent(this, LoginActivity::class.java).also {
                Toast.makeText(this, "Berhasil Logout", Toast.LENGTH_SHORT).show()
                startActivity(it)
                finish()
            }
        }

        binding.btnAddData.setOnClickListener {
            Intent(this, AdminAddActivity::class.java).also {
                startActivity(it)
                finish()
            }
        }

        binding.btnToHome.setOnClickListener {
            Intent(this, AdminHomeActivity::class.java).also {
                startActivity(it)
                finish()
            }
        }

        adminCakeAdapter = HomeAdminCakeAdapter {
            navigateToDetailDataActivity(it)
        }
        binding.rvAdmin.adapter = adminCakeAdapter
        binding.rvAdmin.layoutManager = LinearLayoutManager(this)
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
                for (document in result) {
                    val cakeData = document.toObject(Cake::class.java)
                    cakeList.add(cakeData)
                }
                adminCakeAdapter.submitList(cakeList)
                adminCakeAdapter.sortDataByName()
                Log.d("AdminActivity", "Fetched data: $cakeList")
            }
            .addOnFailureListener { exception ->
                Log.e("AdminActivity", "Error fetching data", exception)
                Toast.makeText(
                    this,
                    "Error fetching data: ${exception.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun navigateToDetailDataActivity(cakeId: String) {
        val intent = Intent(this, CakeDetailActivity::class.java)
        intent.putExtra("documentId", cakeId)
        startActivity(intent)
    }
}