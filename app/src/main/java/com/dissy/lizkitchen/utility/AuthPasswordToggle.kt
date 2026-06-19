package com.dissy.lizkitchen.utility

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import com.dissy.lizkitchen.R
import kotlin.math.roundToInt

@SuppressLint("ClickableViewAccessibility")
fun AppCompatEditText.setupPasswordVisibilityToggle() {
    var isPasswordVisible = false
    val touchSlop = (16 * resources.displayMetrics.density).roundToInt()

    fun updatePasswordState() {
        transformationMethod = if (isPasswordVisible) {
            HideReturnsTransformationMethod.getInstance()
        } else {
            PasswordTransformationMethod.getInstance()
        }

        val icon = ContextCompat.getDrawable(
            context,
            if (isPasswordVisible) R.drawable.baseline_visibility_off_24 else R.drawable.baseline_visibility_24
        )
        setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, icon, null)
        compoundDrawablePadding = (12 * resources.displayMetrics.density).roundToInt()
        setSelection(text?.length ?: 0)
    }

    updatePasswordState()
    setOnTouchListener { _, event ->
        if (!isEnabled || event.action != MotionEvent.ACTION_UP) return@setOnTouchListener false

        val endDrawable = compoundDrawablesRelative[2] ?: return@setOnTouchListener false
        val isRtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
        val iconTouched = if (isRtl) {
            event.x <= paddingStart + endDrawable.bounds.width() + touchSlop
        } else {
            event.x >= width - paddingEnd - endDrawable.bounds.width() - touchSlop
        }

        if (!iconTouched) return@setOnTouchListener false

        isPasswordVisible = !isPasswordVisible
        updatePasswordState()
        true
    }
}

fun Activity.hideKeyboardWhenTouchOutsideInput(event: MotionEvent) {
    if (event.action != MotionEvent.ACTION_DOWN) return

    val focusedView = currentFocus as? EditText ?: return
    val rawX = event.rawX.roundToInt()
    val rawY = event.rawY.roundToInt()

    if (isTouchInsideEditText(window.decorView, rawX, rawY)) return

    val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow(focusedView.windowToken, 0)
    focusedView.clearFocus()
}

@SuppressLint("ClickableViewAccessibility")
fun ViewGroup.clearFocusWhenTouchOutsideInput() {
    isFocusableInTouchMode = true

    fun clearFocusIfNeeded(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN) return

        val focusedView = (rootView.findFocus() as? EditText)
            ?: ((context as? Activity)?.currentFocus as? EditText)
            ?: return
        val rawX = event.rawX.roundToInt()
        val rawY = event.rawY.roundToInt()

        if (isTouchInsideEditText(this, rawX, rawY)) return

        val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(focusedView.windowToken, 0)
        focusedView.clearFocus()
        requestFocus()
    }

    fun attach(view: View) {
        if (view !is EditText) {
            view.setOnTouchListener { _, event ->
                clearFocusIfNeeded(event)
                false
            }
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                attach(view.getChildAt(index))
            }
        }
    }

    attach(this)
}

private fun isTouchInsideEditText(view: View, rawX: Int, rawY: Int): Boolean {
    if (view is EditText) {
        val bounds = Rect()
        view.getGlobalVisibleRect(bounds)
        return bounds.contains(rawX, rawY)
    }

    if (view is ViewGroup) {
        for (index in 0 until view.childCount) {
            if (isTouchInsideEditText(view.getChildAt(index), rawX, rawY)) {
                return true
            }
        }
    }

    return false
}
