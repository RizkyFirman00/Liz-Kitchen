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
    private var pendingMethod: String = ""
    private var pendingPickupBranch: LizKitchenBranch? = null

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

    fun setInitialSelection(method: String, pickupBranch: LizKitchenBranch?) {
        pendingMethod = method
        pendingPickupBranch = pickupBranch
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
            branchOptionsContainer.visibility = if (pendingMethod == METODE_AMBIL_SENDIRI) {
                View.VISIBLE
            } else {
                View.GONE
            }
            updateSelectionState()

            btnAntar.setOnClickListener {
                if (!isDeliveryAvailable) return@setOnClickListener
                pendingMethod = METODE_PESAN_ANTAR
                pendingPickupBranch = null
                branchOptionsContainer.visibility = View.GONE
                updateSelectionState()
            }

            btnPickup.setOnClickListener {
                if (pendingMethod != METODE_AMBIL_SENDIRI) pendingPickupBranch = null
                pendingMethod = METODE_AMBIL_SENDIRI
                branchOptionsContainer.visibility = View.VISIBLE
                updateSelectionState()
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
            btnPickupMenteng.setOnClickListener {
                pendingMethod = METODE_AMBIL_SENDIRI
                pendingPickupBranch = mentengBranch
                updateSelectionState()
            }

            btnPickupCengkareng.text = buildBranchButtonText(cengkarengBranch)
            btnPickupCengkareng.setOnClickListener {
                pendingMethod = METODE_AMBIL_SENDIRI
                pendingPickupBranch = cengkarengBranch
                updateSelectionState()
            }

            btnConfirmMethod.setOnClickListener {
                if (!isMethodSelectionValid(
                        pendingMethod,
                        pendingPickupBranch != null,
                        isDeliveryAvailable
                    )
                ) return@setOnClickListener

                listener?.onMetodePengambilanSelected(pendingMethod, pendingPickupBranch)
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

    private fun FragmentMetodeAmbilBinding.updateSelectionState() {
        btnAntar.setBackgroundResource(
            when {
                !isDeliveryAvailable -> R.drawable.shape_button_choice_disabled
                pendingMethod == METODE_PESAN_ANTAR -> R.drawable.shape_button_choice_selected
                else -> R.drawable.shape_button_choice_default
            }
        )
        btnPickup.setBackgroundResource(
            if (pendingMethod == METODE_AMBIL_SENDIRI) {
                R.drawable.shape_button_choice_selected
            } else {
                R.drawable.shape_button_choice_default
            }
        )

        updateBranchCard(btnPickupMenteng, LIZ_KITCHEN_BRANCHES[0])
        updateBranchCard(btnPickupCengkareng, LIZ_KITCHEN_BRANCHES[1])

        val isValid = isMethodSelectionValid(
            pendingMethod,
            pendingPickupBranch != null,
            isDeliveryAvailable
        )
        btnConfirmMethod.isEnabled = isValid
        btnConfirmMethod.alpha = if (isValid) 1f else 0.45f
    }

    private fun updateBranchCard(
        button: androidx.appcompat.widget.AppCompatButton,
        branch: LizKitchenBranch
    ) {
        button.setBackgroundResource(
            if (branch.id == pendingPickupBranch?.id) {
                R.drawable.shape_button_choice_selected
            } else {
                R.drawable.shape_button_choice_default
            }
        )
        button.setTextColor(Color.parseColor("#3A2A20"))
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

internal fun isMethodSelectionValid(
    method: String,
    hasPickupBranch: Boolean,
    isDeliveryAvailable: Boolean
): Boolean = when (method) {
    METODE_PESAN_ANTAR -> isDeliveryAvailable
    METODE_AMBIL_SENDIRI -> hasPickupBranch
    else -> false
}
