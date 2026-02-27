package com.dissy.lizkitchen.ui.admin.user

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.GridLayoutManager
import com.dissy.lizkitchen.adapter.admin.HomeAdminUserAdapter
import com.dissy.lizkitchen.databinding.ActivityAdminUserBinding
import com.dissy.lizkitchen.model.Cake
import com.dissy.lizkitchen.model.Cart
import com.dissy.lizkitchen.model.Order
import com.dissy.lizkitchen.model.User
import com.dissy.lizkitchen.ui.admin.AdminHomeActivity
import com.dissy.lizkitchen.ui.admin.report.ReportActivity
import com.dissy.lizkitchen.ui.base.BaseActivity
import com.dissy.lizkitchen.ui.login.LoginActivity
import com.dissy.lizkitchen.utility.Preferences
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class AdminUserOrderActivity : BaseActivity() {
    private val db = Firebase.firestore
    private lateinit var orderId: String
    private lateinit var adminUserAdapter: HomeAdminUserAdapter
    private var orderList = mutableListOf<Order>()
    private val binding by lazy { ActivityAdminUserBinding.inflate(layoutInflater) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        adminUserAdapter = HomeAdminUserAdapter {
            navigateToDetailDataActivity(it)
        }

        val spanCount = 2
        val layoutManager = GridLayoutManager(this, spanCount)
        binding.rvUser.adapter = adminUserAdapter
        binding.rvUser.layoutManager = layoutManager
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return spanCount / 2
            }
        }

        fetchDataAndUpdateRecyclerView()

        binding.searhView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText != null && newText.isNotEmpty()) {
                    adminUserAdapter.filter.filter(newText)
                } else {
                    adminUserAdapter.submitList(orderList)
                }
                return true
            }
        })

        binding.btnToMutasi.setOnClickListener {
            Intent(this, ReportActivity::class.java).also {
                startActivity(it)
                finish()
            }
        }

        binding.btnToLogout.setOnClickListener {
            Preferences.logout(this)
            Intent(this, LoginActivity::class.java).also {
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
    }

    private fun fetchDataAndUpdateRecyclerView() {
        db.collection("orders").get().addOnSuccessListener { result ->
            for (document in result) {
                val cartItemsArray = document.get("cart") as? ArrayList<HashMap<String, Any>>
                val cartItems = cartItemsArray?.map { map ->
                    Cart(
                        cakeId = map["cakeId"] as? String ?: "",
                        cake = Cake(
                            documentId = map["documentId"] as? String ?: "",
                            harga = map["harga"] as? String ?: "",
                            imageUrl = map["imageUrl"] as? String ?: "",
                            namaKue = map["namaKue"] as? String ?: "",
                            stok = (map["stok"] as? Long) ?: 0
                        ),
                        jumlahPesanan = (map["jumlahPesanan"] as? Long) ?: 0
                    )
                } ?: listOf()
                val userInfo = document.get("user") as? HashMap<String, Any>
                val order = Order(
                    cart = cartItems,
                    orderId = document.getString("orderId") ?: "",
                    status = document.getString("status") ?: "",
                    totalPrice = document.getLong("totalPrice") ?: 0,
                    tanggalOrder = document.getString("tanggalOrder") ?: "",
                    user = userInfo?.let {
                        User(
                            userId = it["userId"] as? String ?: "",
                            username = it["username"] as? String ?: "",
                            email = it["email"] as? String ?: "",
                            phoneNumber = it["phoneNumber"] as? String ?: "",
                            alamat = it["alamat"] as? String ?: ""
                        )
                    } ?: User()
                )
                orderId = order.orderId
                Log.d("AdminUserAct", "orderId: $orderId")
                orderList.add(order)
            }
            Log.d("AdminUserAct", "onSuccess: $orderList")
            val sortedOrderList = orderList.sortedByDescending { it.tanggalOrder }
            adminUserAdapter.submitList(sortedOrderList)
        }.addOnFailureListener { exception ->
            Log.d("TAG", "Error getting documents: ", exception)
        }
    }


    private fun navigateToDetailDataActivity(orderId: String) {
        val intent = Intent(this, AdminUserOrderDetailActivity::class.java)
        intent.putExtra("orderId", orderId)
        startActivity(intent)
        finish()
    }
}