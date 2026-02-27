package com.dissy.lizkitchen.ui.home

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.dissy.lizkitchen.R
import com.dissy.lizkitchen.databinding.ActivityMainBinding
import com.dissy.lizkitchen.ui.base.BaseActivity
import com.dissy.lizkitchen.ui.cart.CartFragment
import com.dissy.lizkitchen.ui.order.OrderFragment

class MainActivity : BaseActivity() {
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        // Set default fragment
        if (savedInstanceState == null) {
            replaceFragment(HomeFragment())
        }

        // Check intent for fragment to load
        intent.getStringExtra(EXTRA_FRAGMENT_TO_LOAD)?.let { fragmentName ->
            if (fragmentName == FRAGMENT_CART) {
                replaceFragment(CartFragment())
                binding.bottomNavigationView.selectedItemId = R.id.navigation_cart
            }
        }

        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    replaceFragment(HomeFragment())
                    true
                }
                R.id.navigation_cart -> {
                    replaceFragment(CartFragment())
                    true
                }
                R.id.navigation_history -> {
                    replaceFragment(OrderFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        val fragmentManager = supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()
        
        // Cek jika fragment sudah aktif untuk menghindari reload yang tidak perlu
        val currentFragment = fragmentManager.findFragmentById(R.id.fragmentContainerView)
        if (currentFragment?.javaClass == fragment.javaClass) return

        fragmentTransaction.replace(R.id.fragmentContainerView, fragment)
        fragmentTransaction.commit()
    }

    companion object {
        const val EXTRA_FRAGMENT_TO_LOAD = "fragment_to_load"
        const val FRAGMENT_CART = "cart"
    }
}