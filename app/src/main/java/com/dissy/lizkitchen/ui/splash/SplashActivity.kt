package com.dissy.lizkitchen.ui.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import com.dissy.lizkitchen.databinding.ActivitySplashBinding
import com.dissy.lizkitchen.ui.base.BaseActivity
import com.dissy.lizkitchen.ui.login.LoginActivity

class SplashActivity : BaseActivity() {
    private val SPLASH_DELAY: Long = 1200
    private val binding by lazy { ActivitySplashBinding.inflate(layoutInflater) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        Handler().postDelayed({
            val intent = Intent(this@SplashActivity, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }, SPLASH_DELAY)
    }
}