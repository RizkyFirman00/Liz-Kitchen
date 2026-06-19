package com.dissy.lizkitchen.ui.admin.report

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.dissy.lizkitchen.R
import com.dissy.lizkitchen.databinding.FragmentReportBinding
import com.dissy.lizkitchen.utility.clearFocusWhenTouchOutsideInput
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ReportFragment : Fragment() {
    private var _binding: FragmentReportBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.root.clearFocusWhenTouchOutsideInput()

        binding.btnToHome.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnCekMutasi.setOnClickListener {
            val fromDate = binding.etFromDate.text.toString()
            val toDate = binding.etToDate.text.toString()
            if (fromDate.isEmpty() || toDate.isEmpty()) {
                Toast.makeText(requireContext(), "Tolong isi FROM dan TO DATE", Toast.LENGTH_SHORT).show()
            } else {
                if (isDateRangeValid(fromDate, toDate, 14)) {
                    val bundle = Bundle().apply {
                        putString("fromDate", fromDate)
                        putString("toDate", toDate)
                    }
                    findNavController().navigate(R.id.navigation_admin_report_detail, bundle)
                } else {
                    Toast.makeText(requireContext(), "Rentang tanggal tidak boleh lebih dari 14 hari", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.etFromDate.setOnClickListener { showDatePickerDialog(binding.etFromDate) }
        binding.etToDate.setOnClickListener { showDatePickerDialog(binding.etToDate) }
        binding.etLayoutFromDate.setEndIconOnClickListener { showDatePickerDialog(binding.etFromDate) }
        binding.etLayoutToDate.setEndIconOnClickListener { showDatePickerDialog(binding.etToDate) }
    }

    private fun showDatePickerDialog(editText: TextInputEditText) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDay ->
                val selectedDate = "$selectedYear-${selectedMonth + 1}-$selectedDay"
                formatDate(selectedDate)?.let { editText.setText(it) }
            },
            year, month, day
        ).show()
    }

    private fun isDateRangeValid(fromDate: String, toDate: String, maxDays: Int): Boolean {
        return try {
            val inputFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
            val date1 = inputFormat.parse(fromDate)
            val date2 = inputFormat.parse(toDate)
            if (date1 != null && date2 != null) {
                val diffInMillies = Math.abs(date2.time - date1.time)
                val diffInDays = (diffInMillies / (24 * 60 * 60 * 1000)).toInt()
                diffInDays <= maxDays
            } else false
        } catch (e: Exception) { false }
    }

    private fun formatDate(dateString: String): String? {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val outputFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val date = inputFormat.parse(dateString)
        return date?.let { outputFormat.format(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
