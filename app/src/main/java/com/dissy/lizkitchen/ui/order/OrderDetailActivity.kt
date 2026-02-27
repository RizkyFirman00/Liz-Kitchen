package com.dissy.lizkitchen.ui.order

import android.content.Intent
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.dissy.lizkitchen.adapter.admin.CartDetailUserAdapter
import com.dissy.lizkitchen.databinding.ActivityOrderDetailBinding
import com.dissy.lizkitchen.model.Cake
import com.dissy.lizkitchen.model.Cart
import com.dissy.lizkitchen.model.Order
import com.dissy.lizkitchen.model.User
import com.dissy.lizkitchen.ui.base.BaseActivity
import com.dissy.lizkitchen.ui.konfirmasi.ConfirmActivity
import com.dissy.lizkitchen.ui.admin.user.AdminUserOrderActivity // Added import
import com.dissy.lizkitchen.utility.Preferences
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class OrderDetailActivity : BaseActivity() {
    private val db = Firebase.firestore
    private lateinit var userId: String
    private lateinit var orderId: String
    private lateinit var cartDetailUserAdapter: CartDetailUserAdapter
    private val binding by lazy { ActivityOrderDetailBinding.inflate(layoutInflater) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        userId = Preferences.getUserId(this).toString()
        orderId = intent.getStringExtra("orderId").toString()
        Log.d("TAG", "onCreate Detail order $orderId"
        )

        cartDetailUserAdapter = CartDetailUserAdapter()
        binding.rvDetailOrderItem.adapter = cartDetailUserAdapter
        binding.rvDetailOrderItem.layoutManager = LinearLayoutManager(this)
        fetchDataAndUpdateRecyclerView(orderId)

        db.collection("users").document(userId).collection("orders").document(orderId).get()
            .addOnSuccessListener {
                val cartItemsArray = it.get("cart") as? ArrayList<HashMap<String, Any>>
                val cartItems = cartItemsArray?.map { map ->
                    val cakeMap = map["cake"] as? HashMap<*, *>
                    Cart(
                        cakeId = cakeMap?.get("documentId") as? String ?: "",
                        cake = Cake(
                            documentId = cakeMap?.get("documentId") as? String ?: "",
                            harga = cakeMap?.get("harga") as? String ?: "",
                            imageUrl = cakeMap?.get("imageUrl") as? String ?: "",
                            namaKue = cakeMap?.get("namaKue") as? String ?: "",
                            stok = (cakeMap?.get("stok") as? Long) ?: 0
                        ),
                        jumlahPesanan = map["jumlahPesanan"] as? Long ?: 0
                    )
                } ?: listOf()
                val userInfo = it.get("user") as? HashMap<String, Any>
                var order = Order(
                    cart = cartItems,
                    orderId = it.getString("orderId") ?: "",
                    status = it.getString("status") ?: "",
                    totalPrice = it.getLong("totalPrice") ?: 0,
                    metodePengambilan = it.getString("metodePengambilan") ?: "",
                    tanggalOrder = it.getString("tanggalOrder") ?: "Menunggu pembayaran",
                    jamOrder = it.getString("jamOrder") ?: "Menunggu pembayaran",
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
                Log.d("TAG", "Error getting documents: $order")

                binding.apply {
                    tvOrderId.text = order.orderId
                    tvStatus.text = order.status
                    binding.btnToPrint.setOnClickListener {
                        val webView = WebView(this@OrderDetailActivity)
                        webView.loadDataWithBaseURL(null, generateOrderHtml(order = order), "text/HTML", "UTF-8", null)

                        val printManager = getSystemService(PRINT_SERVICE) as PrintManager
                        val printAdapter = webView.createPrintDocumentAdapter("Order")

                        val printJob = printManager.print(
                            "OrderDocument",
                            printAdapter,
                            PrintAttributes.Builder().build()
                        )
                    }
                    when (order.status) {
                        "Selesai" -> {
                            binding.tvStatus.setTextColor(android.graphics.Color.parseColor("#0ACB12"))
                            binding.btnConfirm.visibility = View.GONE
                            binding.btnCancel.visibility = View.GONE
                        }

                        "Dibatalkan" -> {
                            binding.tvStatus.setTextColor(android.graphics.Color.parseColor("#D10826"))
                            binding.btnConfirm.visibility = View.GONE
                            binding.btnCancel.visibility = View.GONE
                        }

                        "Menunggu Pembayaran" -> {
                            binding.tvStatus.setTextColor(android.graphics.Color.parseColor("#D10826"))
                        }

                        "Sedang Dikirim" -> {
                            binding.tvStatus.setTextColor(android.graphics.Color.parseColor("#0ACB12"))
                            binding.btnCancel.visibility = View.GONE
                            binding.btnReceive.visibility = View.VISIBLE
                            binding.btnConfirm.visibility = View.GONE
                        }

                        "Sudah Dikonfirmasi" -> {
                            binding.tvStatus.setTextColor(android.graphics.Color.parseColor("#0ACB12"))
                            binding.btnConfirm.visibility = View.GONE
                            binding.btnCancel.visibility = View.GONE
                        }

                        "Sedang Diproses" -> {
                            binding.tvStatus.setTextColor(android.graphics.Color.parseColor("#9C6843"))
                            binding.btnConfirm.visibility = View.GONE
                            binding.btnCancel.visibility = View.GONE
                        }
                    }
                    tvAlamat.text = order.user.alamat
                    tvOrderId.text = order.orderId
                    tvJamOrder.text = order.jamOrder
                    tvOrderDate.text = order.tanggalOrder
                    tvMetodePengambilan.text = order.metodePengambilan
                    val priceSum = formatAndDisplayCurrency(order.totalPrice.toString())
                    tvPriceSum.text = priceSum
                }
            }.addOnFailureListener { exception ->
                Log.d("TAG", "Error getting documents: ", exception)
            }

        binding.btnToHome.setOnClickListener {
            Intent(this, AdminUserOrderActivity::class.java).also { // Corrected navigation
                startActivity(it)
                finish()
            }
        }

        binding.btnConfirm.setOnClickListener {
            Intent(this, ConfirmActivity::class.java).also {
                it.putExtra("orderId", orderId)
                startActivity(it)
            }
        }

        binding.btnReceive.setOnClickListener {
            db.collection("users").document(userId).collection("orders").document(orderId)
                .update("status", "Selesai")
                .addOnSuccessListener {
                    db.collection("orders").document(orderId).update("status", "Selesai")
                        .addOnSuccessListener {
                            Log.d("TAG", "Pesanan berhasil diterima")
                        }.addOnFailureListener {
                            Toast.makeText(this, "Pesanan gagal diterima", Toast.LENGTH_SHORT)
                                .show()
                        }
                    Toast.makeText(this, "Pesanan berhasil diterima", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, AdminUserOrderActivity::class.java) // Corrected navigation
                    startActivity(intent)
                    finish()
                }.addOnFailureListener {
                    Toast.makeText(this, "Pesanan gagal diterima", Toast.LENGTH_SHORT).show()
                }
        }

        binding.btnCancel.setOnClickListener {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Konfirmasi Pembatalan")
            builder.setMessage("Apakah Anda yakin ingin membatalkan pesanan ini?")

            builder.setPositiveButton("Ya") { _, _ ->
                cancelOrder()
            }

            builder.setNegativeButton("Tidak") { _, _ ->

            }

            val dialog = builder.create()
            dialog.show()
        }
    }

    private fun fetchDataAndUpdateRecyclerView(orderId: String) {
        db.collection("users").document(userId).collection("orders").document(orderId).get()
            .addOnSuccessListener {
                val cartList = mutableListOf<Cart>()
                val cartItemsArray = it.get("cart") as? ArrayList<HashMap<String, Any>>
                val cartItems = cartItemsArray?.map { map ->
                    val cakeMap = map["cake"] as? HashMap<String, Any>
                    Cart(
                        cakeId = map["cakeId"] as? String ?: "",
                        cake = Cake(
                            documentId = cakeMap?.get("documentId") as? String ?: "",
                            namaKue = cakeMap?.get("namaKue") as? String ?: "",
                            harga = cakeMap?.get("harga") as? String ?: "",
                            stok = cakeMap?.get("stok") as? Long ?: 0,
                            imageUrl = cakeMap?.get("imageUrl") as? String ?: ""
                        ),
                        jumlahPesanan = map["jumlahPesanan"] as? Long ?: 0
                    )
                } ?: listOf()
                cartList.addAll(cartItems)
                Log.d("TAG", "Cart List Detail Order: $cartList")
                cartDetailUserAdapter.submitList(cartItems)
            }.addOnFailureListener { exception ->
                Log.d("TAG", "Error getting documents: ", exception)
            }
    }

    private fun generateOrderHtml(order: Order): String {
        val orderHtml = """
        <html>
            <head>
                <title>Order Detail</title>
                <style>
                    body {
                        font-family: Arial, sans-serif;
                        margin: 20px;
                    }
                    h1 {
                        color: #333333;
                    }
                    p {
                        margin-bottom: 10px;
                    }
                    table {
                        width: 100%;
                        border-collapse: collapse;
                        margin-top: 20px;
                    }
                    th, td {
                        border: 1px solid #dddddd;
                        text-align: left;
                        padding: 8px;
                    }
                    th {
                        background-color: #f2f2f2;
                    }
                </style>
            </head>
            <body>
                <h1>Order Detail</h1>                
                <p><strong>Order ID :</strong> ${order.orderId}</p>
                <p><strong>Status :</strong> ${order.status}</p>
                <p><strong>Metode Pemesanan :</strong> ${order.metodePengambilan}</p>
                <p><strong>Tanggal Order :</strong> ${order.tanggalOrder}</p>

                <br>
                <h2>Pemesan</h2>
                <p><strong>Username :</strong> ${order.user.username}</p>
                <p><strong>Nomor Telepon Pemesan :</strong> ${order.user.phoneNumber}</p>
                <p><strong>Alamat Pemesan :</strong> ${order.user.alamat}</p>
                
                <br>
                <h2>Kue Dipesan</h2>
                <table>
                    <thead>
                        <tr>
                            <th>Cake Name</th>
                            <th>Quantity</th>
                            <th>Price</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${generateCartItemsHtml(order.cart)}
                    </tbody>
                </table>

                <br>
                <h2>Harga Total</h2>
                <table>
                    <tbody>
                        <tr>
                            <td>Total Price:</td>
                            <td>${formatAndDisplayCurrency(order.totalPrice.toString())}</td>
                        </tr>
                    </tbody>
                </table>

                <style>
                    /* Add custom styles here */
                </style>
            </body>
        </html>
    """.trimIndent()

        return orderHtml
    }

    private fun generateCartItemsHtml(cart: List<Cart>): String {
        val cartItemsHtml = StringBuilder()
        for (item in cart) {
            cartItemsHtml.append("<tr>")
                .append("<td>${item.cake.namaKue}</td>")
                .append("<td>${item.jumlahPesanan}</td>")
                .append("<td>${formatAndDisplayCurrency(item.cake.harga)}</td>")
                .append("</tr>")
        }
        return cartItemsHtml.toString()
    }

    private fun cancelOrder() {
        db.collection("users").document(userId).collection("orders").document(orderId)
            .update("status", "Dibatalkan")
            .addOnSuccessListener {
                Log.d("cancelOrder", "Pesanan berhasil dibatalkan")
            }.addOnFailureListener {
                Toast.makeText(this, "Pesanan gagal dibatalkan", Toast.LENGTH_SHORT).show()
            }

        db.collection("orders").document(orderId).update("status", "Dibatalkan")
            .addOnSuccessListener {
                Toast.makeText(this, "Pesanan berhasil dibatalkan", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, AdminUserOrderActivity::class.java) // Corrected navigation
                startActivity(intent)
                finish()
            }.addOnFailureListener {
                Toast.makeText(this, "Pesanan gagal dibatalkan", Toast.LENGTH_SHORT).show()
            }
    }

    private fun formatAndDisplayCurrency(value: String): String {
        val isNegative = value.startsWith("-")
        val cleanValue = if (isNegative) value.substring(1) else value

        val stringBuilder = StringBuilder(cleanValue)
        val length = stringBuilder.length
        var i = length - 3
        while (i > 0) {
            stringBuilder.insert(i, ".")
            i -= 3
        }
        val formattedText = if (isNegative) {
            stringBuilder.insert(0, "-").toString()
        } else {
            stringBuilder.toString()
        }

        return "Rp. $formattedText"
    }
}