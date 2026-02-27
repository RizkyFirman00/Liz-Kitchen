package com.dissy.lizkitchen.ui.admin.report

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.PopupMenu
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.dissy.lizkitchen.R
import com.dissy.lizkitchen.adapter.admin.ReportAdminAdapter
import com.dissy.lizkitchen.databinding.ActivityReportDetailBinding
import com.dissy.lizkitchen.model.Cake
import com.dissy.lizkitchen.model.Cart
import com.dissy.lizkitchen.model.Order
import com.dissy.lizkitchen.model.User
import com.dissy.lizkitchen.ui.base.BaseActivity
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

class ReportDetailActivity : BaseActivity() {
    private lateinit var reportAdminAdapter: ReportAdminAdapter
    private lateinit var banyakData: String
    private var orderList = mutableListOf<Order>()
    private val db = Firebase.firestore
    private val binding by lazy { ActivityReportDetailBinding.inflate(layoutInflater) }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        val fromDate = intent.getStringExtra("fromDate")
        val toDate = intent.getStringExtra("toDate")
        Toast.makeText(this, "$fromDate, $toDate", Toast.LENGTH_SHORT).show()

        binding.apply {
            tvFromDate.text = fromDate
            tvToDate.text = toDate
            val popupMenu = PopupMenu(this@ReportDetailActivity, binding.appCompatImageButton)
            popupMenu.menuInflater.inflate(R.menu.status_menu, popupMenu.menu)
            appCompatImageButton.setOnClickListener {
                popupMenu.show()
            }
            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.menu_semua -> {
                        // Memperbarui status dan memuat ulang data
                        updateStatusFilter("Semua")
                        true
                    }

                    R.id.menu_selesai -> {
                        updateStatusFilter("Selesai")
                        true
                    }

                    R.id.menu_dibatalkan -> {
                        updateStatusFilter("Dibatalkan")
                        true
                    }

                    R.id.menu_menungguPembayaran -> {
                        updateStatusFilter("Menunggu Pembayaran")
                        true
                    }

                    R.id.menu_sedangDikirim -> {
                        updateStatusFilter("Sedang Dikirim")
                        true
                    }

                    R.id.menu_sudahDikonfirmasi -> {
                        updateStatusFilter("Sudah Dikonfirmasi")
                        true
                    }

                    R.id.menu_sedangDiproses -> {
                        updateStatusFilter("Sedang Diproses")
                        true
                    }

                    R.id.menu_ambilSendiri -> {
                        updateStatusFilter("Ambil Sendiri")
                        true
                    }

                    R.id.menu_pesanAntar -> {
                        updateStatusFilter("Pesan Antar")
                        true
                    }

                    else -> false
                }
            }
        }

        binding.btnToHome.setOnClickListener {
            finish()
        }

        reportAdminAdapter = ReportAdminAdapter()
        binding.rvMutasi.adapter = reportAdminAdapter
        binding.rvMutasi.layoutManager = LinearLayoutManager(this)

        db.collection("orders")
            .whereGreaterThanOrEqualTo("tanggalOrder", fromDate ?: "")
            .whereLessThanOrEqualTo("tanggalOrder", toDate ?: "")
            .get()
            .addOnSuccessListener { result ->
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
                    orderList.add(order)
                }
                Log.d("AdminUserAct", "onSuccess: $orderList")
                reportAdminAdapter.submitList(orderList)

                banyakData = orderList.size.toString()
                binding.tvBanyakData.text = banyakData

            }.addOnFailureListener { exception ->
                Log.d("TAG", "Error getting documents: ", exception)
            }
        binding.btnToPrint.setOnClickListener {
            val webView = WebView(applicationContext)
            createPdfFromWebView(webView)
        }
    }

    private fun updateStatusFilter(status: String) {
        val filteredList = if (status == "Semua") {
            orderList
        } else {
            orderList.filter { it.status == status }
        }
        reportAdminAdapter.submitList(filteredList)
        binding.tvBanyakData.text = filteredList.size.toString()
        binding.tvStatusPesanan.text = status
    }

    private fun createPdfFromWebView(webView: WebView) {
        webView.loadDataWithBaseURL(
            null, generateOrderHtml(
                orderList,
                binding.tvFromDate.text.toString(),
                binding.tvToDate.text.toString(),
                banyakData,
                binding.tvStatusPesanan.text.toString()
            ), "text/HTML", "UTF-8", null
        )

        val printManager = getSystemService(PRINT_SERVICE) as PrintManager
        val printAdapter = webView.createPrintDocumentAdapter("Order")

        printToPdf(printManager, printAdapter)
    }

    private fun printToPdf(printManager: PrintManager, printAdapter: PrintDocumentAdapter) {
        val jobName = getString(R.string.app_name) + " Document"
        val printJob = printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
        if (printJob.isCompleted) {
            Toast.makeText(this, "Printing completed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateOrderHtml(
        orders: List<Order>,
        fromDate: String,
        toDate: String,
        banyakData: String,
        statusPesanan: String
    ): String {
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
                    .card {
                        margin-bottom: 15px;
                        padding: 10px;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        border-radius: 8px;
                        box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
                    }
                    .card .info {
                        flex: 1;
                    }
                    .order-table {
                        width: 100%;
                        margin-top: 10px;
                    }
                    .order-table th, .order-table td {
                        border: 1px solid #dddddd;
                        text-align: left;
                        padding: 8px;
                    }
                    .order-table th {
                        background-color: #f2f2f2;
                    }
                </style>
            </head>
            <body>
                <h1>Order Detail</h1>                
                <div class="card">
                    <div class="info">
                        <p><strong>From date order :</strong> ${fromDate}</p>
                        <p><strong>To date order :</strong> ${toDate}</p>
                        <p><strong>Banyak data :</strong> ${banyakData}</p>
                        <p><strong>Status pesanan :</strong> ${statusPesanan}</p>
                    </div>
                </div>

                <table class="order-table">
                    <thead>
                        <tr>
                        <th>Tanggal Order</th>                   
                        <th>Order ID</th>
                        <th>Username</th>
                        <th>Status</th>
                        <th>Total Harga</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${generateOrderItemsHtml(orders)}
                    </tbody>
                </table>
            </body>
        </html>
    """.trimIndent()

        return orderHtml
    }

    private fun generateOrderItemsHtml(order: List<Order>): String {
        val cartItemsHtml = StringBuilder()
        for (item in order) {
            cartItemsHtml.append("<tr>")
                .append("<td>${item.tanggalOrder}</td>")
                .append("<td>${item.orderId}</td>")
                .append("<td>${item.user.username}</td>")
                .append("<td>${item.status}</td>")
                .append("<td>${formatAndDisplayCurrency(item.totalPrice.toString())}</td>")
                .append("</tr>")
        }
        return cartItemsHtml.toString()
    }


    private fun formatAndDisplayCurrency(value: String): String {
        // Tandai apakah angka negatif
        val isNegative = value.startsWith("-")
        val cleanValue = if (isNegative) value.substring(1) else value

        // Format ulang angka dengan menambahkan titik setiap 3 angka
        val stringBuilder = StringBuilder(cleanValue)
        val length = stringBuilder.length
        var i = length - 3
        while (i > 0) {
            stringBuilder.insert(i, ".")
            i -= 3
        }

        // Tambahkan tanda minus kembali jika angka negatif
        val formattedText = if (isNegative) {
            stringBuilder.insert(0, "-").toString()
        } else {
            stringBuilder.toString()
        }

        return "Rp. $formattedText"
    }

}