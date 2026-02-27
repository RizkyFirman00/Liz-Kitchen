package com.dissy.lizkitchen.ui.base

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.dissy.lizkitchen.R

open class BaseActivity : AppCompatActivity() {

    override fun startActivity(intent: Intent?) {
        super.startActivity(intent)
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }
}