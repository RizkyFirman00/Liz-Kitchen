package com.dissy.lizkitchen.ui.cart

import android.os.Bundle
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.dissy.lizkitchen.R
import com.dissy.lizkitchen.databinding.FragmentMetodeAmbilBinding
import com.dissy.lizkitchen.utility.LIZ_KITCHEN_BRANCHES
import com.dissy.lizkitchen.utility.LizKitchenBranch
import com.dissy.lizkitchen.utility.METODE_AMBIL_SENDIRI
import com.dissy.lizkitchen.utility.METODE_PESAN_ANTAR
import com.dissy.lizkitchen.utility.branchLocationLabel
import com.dissy.lizkitchen.utility.deliveryFeeForDistanceMeters
import com.dissy.lizkitchen.utility.deliveryFeeLabel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.util.Locale

class MetodeAmbilFragment : BottomSheetDialogFragment() {

    interface MetodePengambilanListener {
        fun onMetodePengambilanSelected(metode: String, pickupBranch: LizKitchenBranch?)
    }

    private var listener: MetodePengambilanListener? = null
    private var isDeliveryAvailable: Boolean = true
    private var deliveryNotice: String = ""
    private var recommendedBranch: LizKitchenBranch? = null
    private var recommendedDistanceMeters: Float? = null

    fun setListener(listener: MetodePengambilanListener) {
        this.listener = listener
    }

    fun setDeliveryAvailability(isAvailable: Boolean, notice: String) {
        isDeliveryAvailable = isAvailable
        deliveryNotice = notice
    }

    fun setBranchRecommendation(branch: LizKitchenBranch?, distanceMeters: Float?) {
        recommendedBranch = branch
        recommendedDistanceMeters = distanceMeters
    }

    private val binding by lazy { FragmentMetodeAmbilBinding.inflate(layoutInflater) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        binding.apply {
            val mentengBranch = LIZ_KITCHEN_BRANCHES[0]
            val cengkarengBranch = LIZ_KITCHEN_BRANCHES[1]

            tvDeliveryNotice.text = deliveryNotice.ifBlank {
                "Delivery tersedia untuk alamat hingga 40 km dari cabang."
            }

            bindDeliveryCard()
            updateMethodCards(isPickupActive = false)

            btnAntar.setOnClickListener {
                if (!isDeliveryAvailable) return@setOnClickListener
                listener?.onMetodePengambilanSelected(METODE_PESAN_ANTAR, null)
                dismiss()
            }

            branchOptionsContainer.visibility = View.GONE
            btnPickup.setOnClickListener {
                branchOptionsContainer.visibility = View.VISIBLE
                updateMethodCards(isPickupActive = true)
            }

            tvDeliveryRates.visibility = View.GONE
            btnToggleRates.setOnClickListener {
                val showRates = tvDeliveryRates.visibility != View.VISIBLE
                tvDeliveryRates.visibility = if (showRates) View.VISIBLE else View.GONE
                btnToggleRates.text = if (showRates) {
                    "Sembunyikan rincian tarif pengiriman"
                } else {
                    "Lihat rincian tarif pengiriman"
                }
            }

            btnPickupMenteng.text = buildBranchButtonText(mentengBranch)
            applyBranchRecommendationStyle(btnPickupMenteng, mentengBranch)
            btnPickupMenteng.setOnClickListener {
                listener?.onMetodePengambilanSelected(METODE_AMBIL_SENDIRI, mentengBranch)
                dismiss()
            }

            btnPickupCengkareng.text = buildBranchButtonText(cengkarengBranch)
            applyBranchRecommendationStyle(btnPickupCengkareng, cengkarengBranch)
            btnPickupCengkareng.setOnClickListener {
                listener?.onMetodePengambilanSelected(METODE_AMBIL_SENDIRI, cengkarengBranch)
                dismiss()
            }
        }

        return binding.root
    }

    private fun FragmentMetodeAmbilBinding.bindDeliveryCard() {
        btnAntar.isEnabled = isDeliveryAvailable
        btnAntar.alpha = if (isDeliveryAvailable) 1f else 0.55f
        tvDeliveryTitle.text = if (isDeliveryAvailable) METODE_PESAN_ANTAR else "Pesan Antar Tidak Tersedia"
        tvDeliveryDescription.text = if (isDeliveryAvailable) {
            "Dikirim dari cabang terdekat ke alamatmu."
        } else {
            "Alamat berada di luar jangkauan maksimal 40 km."
        }

        val branch = recommendedBranch
        val distance = recommendedDistanceMeters
        val distanceText = distance?.let { formatDistance(it) }
        val feeText = distance?.let { deliveryFeeForDistanceMeters(it) }?.let { deliveryFeeLabel(it) }

        tvDeliveryDistance.text = distanceText ?: "Jarak dihitung"
        tvDeliveryFee.text = feeText?.let { "Ongkir $it" } ?: "Ongkir dihitung"
        if (branch == null) {
            deliveryBranchInfo.visibility = View.GONE
        } else {
            deliveryBranchInfo.visibility = View.VISIBLE
            tvDeliveryBranchName.text = branch.name
            tvDeliveryBranchAddress.text = branch.address
        }
    }

    private fun applyBranchRecommendationStyle(
        button: androidx.appcompat.widget.AppCompatButton,
        branch: LizKitchenBranch
    ) {
        val isRecommended = branch.id == recommendedBranch?.id
        if (isRecommended) {
            button.setBackgroundResource(R.drawable.shape_button_choice_selected)
            button.setTextColor(Color.parseColor("#3A2A20"))
        } else {
            button.setBackgroundResource(R.drawable.shape_button_choice_default)
            button.setTextColor(Color.parseColor("#3A2A20"))
        }
    }

    private fun FragmentMetodeAmbilBinding.updateMethodCards(isPickupActive: Boolean) {
        btnAntar.setBackgroundResource(
            when {
                !isDeliveryAvailable -> R.drawable.shape_button_choice_disabled
                !isPickupActive -> R.drawable.shape_button_choice_selected
                else -> R.drawable.shape_button_choice_default
            }
        )
        btnPickup.setBackgroundResource(
            if (isPickupActive) {
                R.drawable.shape_button_choice_selected
            } else {
                R.drawable.shape_button_choice_default
            }
        )
    }

    private fun buildBranchButtonText(branch: LizKitchenBranch): String {
        val area = branchLocationLabel(branch)
        return "${branch.name}\n$area"
    }

    private fun formatDistance(distanceMeters: Float): String {
        return if (distanceMeters >= 1_000f) {
            String.format(Locale("id", "ID"), "%.1f km", distanceMeters / 1_000f)
        } else {
            "${distanceMeters.toInt()} m"
        }
    }

}
