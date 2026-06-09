package com.dissy.lizkitchen.ui.cart

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.dissy.lizkitchen.R
import com.dissy.lizkitchen.adapter.user.CheckoutUserAdapter
import com.dissy.lizkitchen.databinding.FragmentDetailCartBinding
import com.dissy.lizkitchen.model.Cake
import com.dissy.lizkitchen.model.Cart
import com.dissy.lizkitchen.model.User
import com.dissy.lizkitchen.utility.Preferences
import com.dissy.lizkitchen.utility.cakeFromMap
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class DetailCartFragment : Fragment() {
    private var _binding: FragmentDetailCartBinding? = null
    private val binding get() = _binding!!
    private val db = Firebase.firestore
    private lateinit var checkoutAdapter: CheckoutUserAdapter
    private var orderId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetailCartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        orderId = arguments?.getString("orderId")
        checkoutAdapter = CheckoutUserAdapter()
        binding.rvCheckout.adapter = checkoutAdapter
        binding.rvCheckout.layoutManager = LinearLayoutManager(requireContext())
        fetchDataAndUpdateRecyclerView()

        val userId = Preferences.getUserId(requireContext())
        if (userId != null && orderId != null) {
            db.collection("users").document(userId).get()
                .addOnSuccessListener {
                    val alamat = it.getString("alamat") ?: "Belum ada alamat"
                    binding.etAlamat.setText(alamat)
                }

            db.collection("users").document(userId).collection("orders").document(orderId!!)
                .get()
                .addOnSuccessListener {
                    val totalHarga = it.getLong("totalPrice") ?: 0
                    binding.tvPriceSum.text = formatAndDisplayCurrency(totalHarga.toString())
                }
        }

        binding.btnToHome.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnGantiMetodePengambilan.setOnClickListener {
            val metodeAmbilFragment = MetodeAmbilFragment()
            metodeAmbilFragment.setListener(object : MetodeAmbilFragment.MetodePengambilanListener {
                override fun onMetodePengambilanSelected(metode: String) {
                    binding.tvMetodePengambilan.text = metode
                }
            })
            metodeAmbilFragment.show(childFragmentManager, metodeAmbilFragment.tag)
        }

        binding.btnCancel.setOnClickListener {
            cancelOrder()
        }

        binding.btnCheckout.setOnClickListener {
            checkout()
        }
    }

    private fun cancelOrder() {
        val userId = Preferences.getUserId(requireContext())
        if (userId != null && orderId != null) {
            val updates = mapOf("status" to "Dibatalkan")
            db.collection("users").document(userId).collection("orders").document(orderId!!).update(updates)
            db.collection("orders").document(orderId!!).update(updates)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Pesanan berhasil dibatalkan", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
        }
    }

    private fun checkout() {
        binding.progressBar2.visibility = View.VISIBLE
        val metodePengambilan = binding.tvMetodePengambilan.text.toString()
        val alamat = binding.etAlamat.text.toString()

        if (alamat.isEmpty()) {
            Toast.makeText(requireContext(), "Alamat tidak boleh kosong", Toast.LENGTH_SHORT).show()
            binding.progressBar2.visibility = View.GONE
            return
        }

        if (metodePengambilan == "Pilih Metode Pengambilan") {
            Toast.makeText(requireContext(), "Silahkan pilih metode pengambilan", Toast.LENGTH_SHORT).show()
            binding.progressBar2.visibility = View.GONE
            return
        }

        val userId = Preferences.getUserId(requireContext())
        if (userId != null && orderId != null) {
            db.collection("users").document(userId).update("alamat", alamat)
            
            val orderUpdates = mapOf(
                "user.alamat" to alamat,
                "metodePengambilan" to metodePengambilan,
                "status" to "Menunggu Pembayaran"
            )

            db.collection("users").document(userId).collection("orders").document(orderId!!).update(orderUpdates)
            db.collection("orders").document(orderId!!).update(orderUpdates)
                .addOnSuccessListener {
                    binding.progressBar2.visibility = View.GONE
                    val bundle = Bundle().apply { putString("orderId", orderId) }
                    findNavController().navigate(R.id.navigation_confirm, bundle)
                    Toast.makeText(requireContext(), "Pesanan berhasil dibuat, Silahkan lakukan pembayaran", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun fetchDataAndUpdateRecyclerView() {
        val userId = Preferences.getUserId(requireContext())
        if (userId != null && orderId != null) {
            db.collection("users").document(userId).collection("orders").document(orderId!!).get()
                .addOnSuccessListener { snapshot ->
                    val cartItemsArray = snapshot.get("cart") as? ArrayList<HashMap<String, Any>>
                    val cartItems = cartItemsArray?.map { map ->
                        val cakeMap = map["cake"] as? HashMap<String, Any>
                        Cart(
                            cakeId = map["cakeId"] as? String ?: "",
                            cake = cakeFromMap(cakeMap?.get("documentId")?.toString().orEmpty(), cakeMap ?: emptyMap<String, Any>()),
                            jumlahPesanan = map["jumlahPesanan"] as? Long ?: 0
                        )
                    } ?: listOf()
                    checkoutAdapter.submitList(cartItems)
                }
        }
    }

    private fun formatAndDisplayCurrency(value: String): String {
        val isNegative = value.startsWith("-")
        val cleanValue = if (isNegative) value.substring(1) else value
        val stringBuilder = StringBuilder(cleanValue)
        var i = stringBuilder.length - 3
        while (i > 0) {
            stringBuilder.insert(i, ".")
            i -= 3
        }
        return if (isNegative) "-$stringBuilder" else stringBuilder.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
