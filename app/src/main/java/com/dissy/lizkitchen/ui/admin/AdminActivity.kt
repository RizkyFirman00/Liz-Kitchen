package com.dissy.lizkitchen.ui.admin

import android.os.Bundle
import androidx.navigation.fragment.NavHostFragment
import com.dissy.lizkitchen.R
import com.dissy.lizkitchen.databinding.ActivityAdminMainBinding
import com.dissy.lizkitchen.ui.base.BaseActivity

class AdminActivity : BaseActivity() {
    private val binding by lazy { ActivityAdminMainBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_admin) as NavHostFragment
        val navController = navHostFragment.navController
        // Admin might not have bottom nav or might have different nav pattern
    }
}