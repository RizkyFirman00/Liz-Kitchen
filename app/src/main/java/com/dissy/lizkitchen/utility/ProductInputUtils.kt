package com.dissy.lizkitchen.utility

import android.widget.EditText
import androidx.core.widget.doAfterTextChanged

val PRODUCT_VARIANT_NAMES = listOf("250 gram", "500 gram", "700 gram")

fun EditText.limitNumericInput(maxDigits: Int, formatThousands: Boolean = false) {
    doAfterTextChanged { editable ->
        val normalized = normalizeNumericInput(editable.toString(), maxDigits)
        val displayValue = if (formatThousands) formatProductPrice(normalized) else normalized
        if (displayValue != editable.toString()) {
            setText(displayValue)
            setSelection(displayValue.length)
        }
    }
}
