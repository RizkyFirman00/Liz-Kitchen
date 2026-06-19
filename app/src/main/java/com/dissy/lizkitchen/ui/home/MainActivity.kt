package com.dissy.lizkitchen.ui.home

import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.dissy.lizkitchen.R
import com.dissy.lizkitchen.databinding.ActivityMainBinding
import com.dissy.lizkitchen.ui.base.BaseActivity

class MainActivity : BaseActivity() {
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private var pendingBottomNavShow: Runnable? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        setupBottomNavigation(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.navigation_home, R.id.navigation_cart, R.id.navigation_history -> {
                    updateBottomNavigation(destination.id)
                    showBottomNavigationAfterContent()
                }
                else -> {
                    hideBottomNavigation()
                }
            }
        }

        // Check intent for fragment to load
        intent.getStringExtra(EXTRA_FRAGMENT_TO_LOAD)?.let { fragmentName ->
            if (fragmentName == FRAGMENT_CART) {
                navController.navigate(R.id.navigation_cart)
            }
        }
    }

    private fun setupBottomNavigation(navController: NavController) {
        binding.bottomNavHome.setOnClickListener {
            navigateTopLevel(navController, R.id.navigation_home)
        }
        binding.bottomNavCart.setOnClickListener {
            navigateTopLevel(navController, R.id.navigation_cart)
        }
        binding.bottomNavHistory.setOnClickListener {
            navigateTopLevel(navController, R.id.navigation_history)
        }
        updateBottomNavigation(navController.currentDestination?.id ?: R.id.navigation_home)
    }

    private fun showBottomNavigationAfterContent() {
        removePendingBottomNavShow()

        val shouldAnimate = binding.bottomNavigationView.visibility != View.VISIBLE ||
            binding.bottomNavigationView.alpha < 1f

        binding.bottomNavigationView.animate().cancel()
        binding.bottomNavigationView.visibility = View.VISIBLE

        if (!shouldAnimate) {
            binding.bottomNavigationView.alpha = 1f
            binding.bottomNavigationView.translationY = 0f
            return
        }

        binding.bottomNavigationView.alpha = 0f
        binding.bottomNavigationView.translationY = resources.getDimensionPixelSize(R.dimen.bottom_nav_height) * 0.25f

        val showAction = Runnable {
            pendingBottomNavShow = null
            binding.bottomNavigationView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(BOTTOM_NAV_ANIMATION_DURATION_MS)
                .start()
        }
        pendingBottomNavShow = showAction

        val navHost = binding.navHostFragment
        navHost.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (navHost.viewTreeObserver.isAlive) {
                    navHost.viewTreeObserver.removeOnPreDrawListener(this)
                }
                if (pendingBottomNavShow === showAction) {
                    binding.bottomNavigationView.postDelayed(showAction, BOTTOM_NAV_SHOW_DELAY_MS)
                }
                return true
            }
        })
    }

    private fun hideBottomNavigation() {
        removePendingBottomNavShow()
        binding.bottomNavigationView.animate().cancel()
        binding.bottomNavigationView.alpha = 0f
        binding.bottomNavigationView.translationY = resources.getDimensionPixelSize(R.dimen.bottom_nav_height) * 0.25f
        binding.bottomNavigationView.visibility = View.GONE
    }

    private fun removePendingBottomNavShow() {
        pendingBottomNavShow?.let { binding.bottomNavigationView.removeCallbacks(it) }
        pendingBottomNavShow = null
    }

    private fun navigateTopLevel(navController: NavController, destinationId: Int) {
        if (navController.currentDestination?.id == destinationId) return

        val navOptions = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setPopUpTo(R.id.navigation_home, false)
            .build()

        navController.navigate(destinationId, null, navOptions)
    }

    private fun updateBottomNavigation(destinationId: Int) {
        setBottomNavigationItem(
            binding.bottomNavHomeIndicator,
            binding.bottomNavHomeIcon,
            binding.bottomNavHomeLabel,
            destinationId == R.id.navigation_home
        )
        setBottomNavigationItem(
            binding.bottomNavCartIndicator,
            binding.bottomNavCartIcon,
            binding.bottomNavCartLabel,
            destinationId == R.id.navigation_cart
        )
        setBottomNavigationItem(
            binding.bottomNavHistoryIndicator,
            binding.bottomNavHistoryIcon,
            binding.bottomNavHistoryLabel,
            destinationId == R.id.navigation_history
        )
    }

    private fun setBottomNavigationItem(
        indicator: View,
        icon: ImageView,
        label: TextView,
        selected: Boolean
    ) {
        val iconColor = if (selected) R.color.brown_old else R.color.brown_nav_inactive
        val textColor = if (selected) R.color.white else R.color.brown_nav_inactive
        val font = if (selected) R.font.poppins_bold else R.font.poppins_medium

        indicator.background = if (selected) {
            ContextCompat.getDrawable(this, R.drawable.shape_bottom_nav_indicator)
        } else {
            null
        }
        icon.setColorFilter(ContextCompat.getColor(this, iconColor))
        label.setTextColor(ContextCompat.getColor(this, textColor))
        label.typeface = ResourcesCompat.getFont(this, font)
    }

    companion object {
        const val EXTRA_FRAGMENT_TO_LOAD = "fragment_to_load"
        const val FRAGMENT_CART = "cart"
        private const val BOTTOM_NAV_SHOW_DELAY_MS = 48L
        private const val BOTTOM_NAV_ANIMATION_DURATION_MS = 140L
    }
}
