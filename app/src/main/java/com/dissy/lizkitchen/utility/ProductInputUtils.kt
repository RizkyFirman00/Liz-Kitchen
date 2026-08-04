package com.dissy.lizkitchen.utility

import android.widget.EditText
import androidx.core.widget.doAfterTextChanged

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
