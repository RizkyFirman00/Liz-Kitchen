package com.dissy.lizkitchen.utility

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.ImageButton
import com.dissy.lizkitchen.R
import com.google.android.material.floatingactionbutton.FloatingActionButton

fun ViewGroup.setButtonsLoading(isLoading: Boolean) {
    forEachButton { button ->
        if (isLoading) {
            if (button.getTag(R.id.tag_button_previous_enabled_state) == null) {
                button.setTag(R.id.tag_button_previous_enabled_state, button.isEnabled)
                button.setTag(R.id.tag_button_previous_alpha, button.alpha)
            }
            button.isEnabled = false
            if (button.visibility == View.VISIBLE) button.alpha = 0.55f
        } else {
            val previousEnabled = button.getTag(R.id.tag_button_previous_enabled_state) as? Boolean
            val previousAlpha = button.getTag(R.id.tag_button_previous_alpha) as? Float
            if (previousEnabled != null) button.isEnabled = previousEnabled
            if (previousAlpha != null) button.alpha = previousAlpha
            button.setTag(R.id.tag_button_previous_enabled_state, null)
            button.setTag(R.id.tag_button_previous_alpha, null)
        }
    }
}

fun ViewGroup.setFirebaseRequestLoading(isLoading: Boolean, progressView: View? = null) {
    progressView?.visibility = if (isLoading) View.VISIBLE else View.GONE
    setButtonsLoading(isLoading)
}

private fun ViewGroup.forEachButton(action: (View) -> Unit) {
    for (index in 0 until childCount) {
        val child = getChildAt(index)
        if (child.isButtonLike()) action(child)
        if (child is ViewGroup) child.forEachButton(action)
    }
}

private fun View.isButtonLike(): Boolean {
    return this is Button || this is ImageButton || this is FloatingActionButton || this is CompoundButton
}
