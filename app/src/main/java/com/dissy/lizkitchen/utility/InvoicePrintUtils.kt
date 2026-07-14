package com.dissy.lizkitchen.utility

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.text.TextUtils
import android.webkit.WebView
import android.webkit.WebViewClient
import com.dissy.lizkitchen.R
import com.dissy.lizkitchen.model.Cart
import com.dissy.lizkitchen.model.Order
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun printOrderInvoice(
    context: Context,
    order: Order,
    onPrintDialogOpened: () -> Unit = {},
    onError: (Throwable) -> Unit = {}
): WebView {
    val webView = WebView(context)
    val jobName = "Invoice ${order.orderId.ifBlank { context.getString(R.string.app_name) }}"
    var hasStartedPrint = false

    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            if (hasStartedPrint) return
            hasStartedPrint = true

            try {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val printAdapter = view.createPrintDocumentAdapter(jobName)
                val printAttributes = PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                    .build()

                printManager.print(jobName, printAdapter, printAttributes)
                onPrintDialogOpened()
            } catch (throwable: Throwable) {
                onError(throwable)
            }
        }
    }

    webView.loadDataWithBaseURL(null, buildInvoiceHtml(order), "text/html", "UTF-8", null)
    return webView
}

private fun buildInvoiceHtml(order: Order): String {
    val totalQuantity = order.cart.sumOf { it.jumlahPesanan }
    val itemTypeCount = order.cart.size
    val addressTitle = if (order.metodePengambilan.contains("ambil", ignoreCase = true)) {
        "Alamat Cabang"
    } else {
        "Alamat Penerima"
    }
    val printedAt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date())

    return """
        <!DOCTYPE html>
        <html>
            <head>
                <meta charset="UTF-8" />
                <style>
                    @page { size: A4; margin: 18mm; }
                    * { box-sizing: border-box; }
                    body {
                        margin: 0;
                        background: #FFF9F5;
                        color: #3A2A20;
                        font-family: Arial, sans-serif;
                        font-size: 12px;
                        line-height: 1.45;
                    }
                    .invoice {
                        overflow: hidden;
                        border: 1px solid #E9D8C7;
                        border-radius: 16px;
                        background: #FFFFFF;
                    }
                    .header {
                        display: flex;
                        justify-content: space-between;
                        gap: 18px;
                        padding: 24px;
                        background: #9C6843;
                        color: #FFFFFF;
                    }
                    .brand { font-size: 22px; font-weight: 700; letter-spacing: 0; }
                    .subtitle { margin-top: 4px; color: #F8EBDD; font-size: 12px; }
                    .invoice-title { font-size: 24px; font-weight: 700; text-align: right; }
                    .invoice-id { margin-top: 4px; color: #F8EBDD; text-align: right; font-size: 11px; }
                    .content { padding: 22px 24px 24px; }
                    .status-row {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        gap: 16px;
                        padding: 14px 16px;
                        border: 1px solid #F0E1D4;
                        border-radius: 12px;
                        background: #FFF9F5;
                    }
                    .status { color: #9C6843; font-size: 15px; font-weight: 700; }
                    .summary { color: #765945; font-size: 12px; text-align: right; }
                    .grid {
                        display: grid;
                        grid-template-columns: 1fr 1fr;
                        gap: 14px;
                        margin-top: 16px;
                    }
                    .box {
                        min-height: 98px;
                        padding: 14px;
                        border: 1px solid #F0E1D4;
                        border-radius: 12px;
                    }
                    .label {
                        color: #9C6843;
                        font-size: 11px;
                        font-weight: 700;
                        text-transform: uppercase;
                    }
                    .value { margin-top: 6px; color: #3A2A20; font-weight: 700; }
                    .muted { color: #765945; font-weight: 400; }
                    table {
                        width: 100%;
                        margin-top: 18px;
                        border-collapse: collapse;
                    }
                    th {
                        padding: 10px 8px;
                        background: #F7E6DA;
                        color: #6D452B;
                        font-size: 11px;
                        text-align: left;
                    }
                    td {
                        padding: 10px 8px;
                        border-bottom: 1px solid #EFE1D4;
                        vertical-align: top;
                    }
                    .number { text-align: right; white-space: nowrap; }
                    .product-name { font-weight: 700; }
                    .product-meta { margin-top: 2px; color: #765945; font-size: 11px; }
                    .total {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        margin-top: 16px;
                        padding: 16px;
                        border-radius: 12px;
                        background: #F7E6DA;
                    }
                    .total-label { font-size: 14px; font-weight: 700; }
                    .total-price { color: #9C6843; font-size: 20px; font-weight: 700; }
                    .footer {
                        margin-top: 20px;
                        color: #765945;
                        font-size: 11px;
                        text-align: center;
                    }
                </style>
            </head>
            <body>
                <section class="invoice">
                    <div class="header">
                        <div>
                            <div class="brand">Liz Kitchen</div>
                            <div class="subtitle">Invoice pesanan pelanggan</div>
                        </div>
                        <div>
                            <div class="invoice-title">INVOICE</div>
                            <div class="invoice-id">${html(order.orderId.ifBlank { "-" })}</div>
                        </div>
                    </div>

                    <div class="content">
                        <div class="status-row">
                            <div>
                                <div class="label">Status Pesanan</div>
                                <div class="status">${html(order.status.ifBlank { "Status belum tersedia" })}</div>
                            </div>
                            <div class="summary">
                                ${itemTypeCount} jenis produk<br/>
                                ${totalQuantity} item
                            </div>
                        </div>

                        <div class="grid">
                            <div class="box">
                                <div class="label">Pelanggan</div>
                                <div class="value">${html(order.user.name.orEmpty().ifBlank { "Pelanggan" })}</div>
                                <div class="muted">${html(order.user.phoneNumber.orEmpty().ifBlank { "-" })}</div>
                                <div class="muted">${html(order.user.email.orEmpty().ifBlank { "-" })}</div>
                            </div>
                            <div class="box">
                                <div class="label">Transaksi</div>
                                <div class="value">${html(order.tanggalOrder.ifBlank { "-" })} ${html(order.jamOrder.ifBlank { "" })}</div>
                                <div class="muted">Metode: ${html(metodePengambilanDisplayForOrder(order).ifBlank { "-" })}</div>
                                <div class="muted">Dicetak: ${html(printedAt)}</div>
                            </div>
                            <div class="box" style="grid-column: span 2;">
                                <div class="label">${html(addressTitle)}</div>
                                <div class="value">${html(invoiceAddress(order))}</div>
                            </div>
                        </div>

                        <table>
                            <thead>
                                <tr>
                                    <th style="width: 36px;">No</th>
                                    <th>Produk</th>
                                    <th class="number">Qty</th>
                                    <th class="number">Harga</th>
                                    <th class="number">Subtotal</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${buildInvoiceRows(order.cart)}
                            </tbody>
                        </table>

                        <div class="total">
                            <div class="total-label">Total Pembayaran</div>
                            <div class="total-price">${formatInvoiceCurrency(order.totalPrice)}</div>
                        </div>

                        <div class="footer">
                            Terima kasih sudah berbelanja di Liz Kitchen.
                        </div>
                    </div>
                </section>
            </body>
        </html>
    """.trimIndent()
}

private fun buildInvoiceRows(items: List<Cart>): String {
    if (items.isEmpty()) {
        return """
            <tr>
                <td colspan="5" style="text-align:center;color:#765945;">Belum ada produk</td>
            </tr>
        """.trimIndent()
    }

    return items.mapIndexed { index, item ->
        val unitPrice = productPriceToLong(item.cake.harga)
        val subtotal = unitPrice * item.jumlahPesanan
        val unit = normalizeProductUnit(item.cake.satuan)
        val variant = item.cake.kategori
            .takeUnless { it.isBlank() || it.equals("Default", ignoreCase = true) }
            ?: "-"

        """
            <tr>
                <td>${index + 1}</td>
                <td>
                    <div class="product-name">${html(item.cake.namaKue.ifBlank { "Produk" })}</div>
                    <div class="product-meta">Varian: ${html(variant)}</div>
                </td>
                <td class="number">${item.jumlahPesanan} ${html(unit)}</td>
                <td class="number">${formatInvoiceCurrency(unitPrice)}</td>
                <td class="number">${formatInvoiceCurrency(subtotal)}</td>
            </tr>
        """.trimIndent()
    }.joinToString("")
}

private fun invoiceAddress(order: Order): String {
    return if (order.metodePengambilan.contains("ambil", ignoreCase = true)) {
        "${pickupBranchNameForOrder(order)} - ${pickupBranchAddressForOrder(order)}"
    } else {
        order.user.alamat?.ifBlank { "Belum ada alamat" } ?: "Belum ada alamat"
    }
}

private fun formatInvoiceCurrency(value: Long): String {
    val isNegative = value < 0
    val cleanValue = if (isNegative) -value else value
    val formatted = StringBuilder(cleanValue.toString())
    var index = formatted.length - 3
    while (index > 0) {
        formatted.insert(index, ".")
        index -= 3
    }
    return if (isNegative) "-Rp $formatted" else "Rp $formatted"
}

private fun html(value: String?): String {
    return TextUtils.htmlEncode(value.orEmpty())
}
