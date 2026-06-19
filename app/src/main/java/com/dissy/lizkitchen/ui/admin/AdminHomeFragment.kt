package com.dissy.lizkitchen.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.dissy.lizkitchen.R
import com.dissy.lizkitchen.databinding.FragmentAdminHomeBinding
import com.dissy.lizkitchen.ui.login.LoginActivity
import com.dissy.lizkitchen.utility.Preferences

class AdminHomeFragment : Fragment() {
    private var _binding: FragmentAdminHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnToCakes.setOnClickListener {
            findNavController().navigate(R.id.navigation_admin_cake)
        }

        binding.btnToUsers.setOnClickListener {
            findNavController().navigate(R.id.navigation_admin_user_order)
        }

        binding.btnToReport.setOnClickListener {
            findNavController().navigate(R.id.navigation_admin_report)
        }

        binding.topAppBar.menu.findItem(R.id.btn_toProfile)?.isVisible = false

        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.btn_toLogout -> {
                    Preferences.logout(requireContext())
                    Intent(requireContext(), LoginActivity::class.java).also {
                        startActivity(it)
                        requireActivity().finish()
                    }
                    true
                }
                else -> false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
