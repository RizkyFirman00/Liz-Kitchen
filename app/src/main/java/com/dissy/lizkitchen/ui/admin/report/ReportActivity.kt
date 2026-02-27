package com.dissy.lizkitchen.ui.admin.report

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.dissy.lizkitchen.R
import com.dissy.lizkitchen.databinding.ActivityReportBinding
import com.dissy.lizkitchen.ui.admin.AdminHomeActivity
import com.dissy.lizkitchen.ui.admin.cake.AdminCakeActivity
import com.dissy.lizkitchen.ui.admin.user.AdminUserOrderActivity
import com.dissy.lizkitchen.ui.base.BaseActivity
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ReportActivity : BaseActivity() {
    private val binding by lazy { ActivityReportBinding.inflate(layoutInflater) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.btnToHome.setOnClickListener {
            Intent(this, AdminUserOrderActivity::class.java).also {
                startActivity(it)
                finish()
            }
        }

        binding.btnCekMutasi.setOnClickListener {
            val fromDate = binding.etFromDate.text.toString()
            val toDate = binding.etToDate.text.toString()
            if (fromDate.isEmpty() || toDate.isEmpty()) {
                Toast.makeText(this, "Tolong isi FROM dan TO DATE", Toast.LENGTH_SHORT).show()
            } else {
                if (isDateRangeValid(fromDate, toDate, 14)) {
                    startActivity(Intent(this, ReportDetailActivity::class.java).also {
                        it.putExtra("fromDate", fromDate)
                        it.putExtra("toDate", toDate)
                    })
                } else {
                    Toast.makeText(this, "Rentang tanggal tidak boleh lebih dari 14 hari", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.etFromDate.setOnClickListener { showDatePickerDialog(binding.etFromDate) }
        binding.etToDate.setOnClickListener { showDatePickerDialog(binding.etToDate) }
    }

    private fun showDatePickerDialog(editText: TextInputEditText) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                val selectedDate = "$selectedYear-${selectedMonth + 1}-$selectedDay"
                val formattedDate = formatDate(selectedDate)

                editText.setText(formattedDate)
            },
            year,
            month,
            day
        )

        datePickerDialog.show()
    }

    private fun isDateRangeValid(fromDate: String, toDate: String, maxDays: Int): Boolean {
        val inputFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val date1 = inputFormat.parse(fromDate)
        val date2 = inputFormat.parse(toDate)

        if (date1 != null && date2 != null) {
            val diffInMillies = Math.abs(date2.time - date1.time)
            val diffInDays = (diffInMillies / (24 * 60 * 60 * 1000)).toInt()

            return diffInDays <= maxDays
        }

        return false
    }


    private fun formatDate(dateString: String): String? {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val outputFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val date = inputFormat.parse(dateString)
        return date?.let { outputFormat.format(it) }
    }

}