package com.dissy.lizkitchen.ui.admin.report

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.dissy.lizkitchen.R
import com.dissy.lizkitchen.adapter.admin.ReportAdminAdapter
import com.dissy.lizkitchen.databinding.FragmentReportDetailBinding
import com.dissy.lizkitchen.model.Order
import com.dissy.lizkitchen.utility.ORDER_STATUS_CANCELED
import com.dissy.lizkitchen.utility.ORDER_STATUS_CONFIRMED
import com.dissy.lizkitchen.utility.ORDER_STATUS_DONE
import com.dissy.lizkitchen.utility.ORDER_STATUS_EXPIRED
import com.dissy.lizkitchen.utility.ORDER_STATUS_PENDING_PAYMENT
import com.dissy.lizkitchen.utility.ORDER_STATUS_PROCESSING
import com.dissy.lizkitchen.utility.ORDER_STATUS_READY_PICKUP
import com.dissy.lizkitchen.utility.ORDER_STATUS_SHIPPING
import com.dissy.lizkitchen.utility.orderFromDocument
import com.dissy.lizkitchen.utility.setFirebaseRequestLoading
import com.dissy.lizkitchen.utility.validateOrderExpiryOnRead
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

class ReportDetailFragment : Fragment() {
    private var _binding: FragmentReportDetailBinding? = null
    private val binding get() = _binding!!
    private lateinit var reportAdminAdapter: ReportAdminAdapter
    private var orderList = mutableListOf<Order>()
    private var currentReportList: List<Order> = emptyList()
    private val db = Firebase.firestore
    private var banyakData: String = "0"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val fromDate = arguments?.getString("fromDate")
        val toDate = arguments?.getString("toDate")

        binding.tvFromDate.text = fromDate
        binding.tvToDate.text = toDate

        val popupMenu = PopupMenu(requireContext(), binding.appCompatImageButton)
        popupMenu.menuInflater.inflate(R.menu.status_menu, popupMenu.menu)
        binding.appCompatImageButton.setOnClickListener { popupMenu.show() }

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_semua -> updateStatusFilter("Semua")
                R.id.menu_selesai -> updateStatusFilter(ORDER_STATUS_DONE)
                R.id.menu_dibatalkan -> updateStatusFilter(ORDER_STATUS_CANCELED)
                R.id.menu_expired -> updateStatusFilter(ORDER_STATUS_EXPIRED)
                R.id.menu_menungguPembayaran -> updateStatusFilter(ORDER_STATUS_PENDING_PAYMENT)
                R.id.menu_sedangDikirim -> updateStatusFilter(ORDER_STATUS_SHIPPING)
                R.id.menu_siapDiambil -> updateStatusFilter(ORDER_STATUS_READY_PICKUP)
                R.id.menu_sudahDikonfirmasi -> updateStatusFilter(ORDER_STATUS_CONFIRMED)
                R.id.menu_sedangDiproses -> updateStatusFilter(ORDER_STATUS_PROCESSING)
                R.id.menu_ambilSendiri -> updateStatusFilter("Ambil Sendiri")
                R.id.menu_pesanAntar -> updateStatusFilter("Pesan Antar")
                else -> false
            }
            true
        }

        binding.btnToHome.setOnClickListener { findNavController().navigateUp() }

        reportAdminAdapter = ReportAdminAdapter()
        binding.rvMutasi.adapter = reportAdminAdapter
        binding.rvMutasi.layoutManager = LinearLayoutManager(requireContext())

        fetchReportData(fromDate, toDate)

        binding.btnToPrint.setOnClickListener {
            val webView = WebView(requireContext())
            createPdfFromWebView(webView)
        }
    }

    private fun fetchReportData(fromDate: String?, toDate: String?) {
        setRequestLoading(true)
        db.collection("orders")
            .whereGreaterThanOrEqualTo("tanggalOrder", fromDate ?: "")
            .whereLessThanOrEqualTo("tanggalOrder", toDate ?: "")
            .get()
            .addOnSuccessListener { result ->
                if (_binding == null) return@addOnSuccessListener
                orderList.clear()
                for (document in result) {
                    orderList.add(validateOrderExpiryOnRead(db, orderFromDocument(document)))
                }
                currentReportList = orderList.toList()
                reportAdminAdapter.submitList(currentReportList)
                updateReportSummary(currentReportList, "Semua")
                setRequestLoading(false)
            }
            .addOnFailureListener { exception ->
                if (_binding != null) {
                    setRequestLoading(false)
                    Toast.makeText(requireContext(), "Gagal memuat laporan: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun updateStatusFilter(status: String) {
        val filteredList = when (status) {
            "Semua" -> orderList
            "Ambil Sendiri", "Pesan Antar" -> orderList.filter { it.metodePengambilan == status }
            else -> orderList.filter { it.status == status }
        }
        currentReportList = filteredList
        reportAdminAdapter.submitList(currentReportList)
        updateReportSummary(currentReportList, status)
    }

    private fun updateReportSummary(list: List<Order>, status: String) {
        banyakData = list.size.toString()
        binding.tvBanyakData.text = banyakData
        binding.tvTotalOmzet.text = formatAndDisplayCurrency(list.sumOf { it.totalPrice }.toString())
        binding.tvStatusPesanan.text = status
    }

    private fun createPdfFromWebView(webView: WebView) {
        webView.loadDataWithBaseURL(
            null, generateOrderHtml(
                currentReportList,
                binding.tvFromDate.text.toString(),
                binding.tvToDate.text.toString(),
                banyakData,
                binding.tvStatusPesanan.text.toString()
            ), "text/HTML", "UTF-8", null
        )

        val printManager = requireContext().getSystemService(Context.PRINT_SERVICE) as PrintManager
        val printAdapter = webView.createPrintDocumentAdapter("Order")
        printManager.print(getString(R.string.app_name) + " Document", printAdapter, PrintAttributes.Builder().build())
    }

    private fun generateOrderHtml(orders: List<Order>, fromDate: String, toDate: String, banyakData: String, statusPesanan: String): String {
        return """
        <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; margin: 20px; }
                    table { width: 100%; border-collapse: collapse; margin-top: 20px; }
                    th, td { border: 1px solid #dddddd; text-align: left; padding: 8px; }
                    th { background-color: #f2f2f2; }
                </style>
            </head>
            <body>
                <h1>Order Detail</h1>                
                <p><strong>From:</strong> $fromDate <strong>To:</strong> $toDate</p>
                <p><strong>Total Items:</strong> $banyakData <strong>Status:</strong> $statusPesanan</p>
                <table>
                    <thead>
                        <tr>
                            <th>Tanggal</th><th>Order ID</th><th>Nama</th><th>Status</th><th>Total</th>
                        </tr>
                    </thead>
                    <tbody>${generateOrderItemsHtml(orders)}</tbody>
                </table>
            </body>
        </html>
        """.trimIndent()
    }

    private fun generateOrderItemsHtml(order: List<Order>): String {
        return order.joinToString("") { item ->
            "<tr><td>${item.tanggalOrder}</td><td>${item.orderId}</td><td>${item.user.name}</td><td>${item.status}</td><td>${formatAndDisplayCurrency(item.totalPrice.toString())}</td></tr>"
        }
    }

    private fun formatAndDisplayCurrency(value: String): String {
        val isNegative = value.startsWith("-")
        val cleanValue = if (isNegative) value.substring(1) else value
        val sb = StringBuilder(cleanValue)
        var i = sb.length - 3
        while (i > 0) { sb.insert(i, "."); i -= 3 }
        return if (isNegative) "-Rp. $sb" else "Rp. $sb"
    }

    private fun setRequestLoading(isLoading: Boolean) {
        if (_binding == null) return
        binding.root.setFirebaseRequestLoading(isLoading)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
