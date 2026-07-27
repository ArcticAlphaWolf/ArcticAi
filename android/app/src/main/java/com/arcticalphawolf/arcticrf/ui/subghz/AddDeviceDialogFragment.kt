package com.arcticalphawolf.arcticrf.ui.subghz

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.arcticalphawolf.arcticrf.R
import com.arcticalphawolf.arcticrf.data.RemoteIcon
import com.arcticalphawolf.arcticrf.databinding.DialogAddDeviceBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AddDeviceDialogFragment(
    private val onSave: (name: String, iconKey: String) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogAddDeviceBinding.inflate(layoutInflater)

        val chipToIcon = mapOf(
            binding.chipGarage.id to RemoteIcon.GARAGE,
            binding.chipGate.id to RemoteIcon.GATE,
            binding.chipDoorbell.id to RemoteIcon.DOORBELL,
            binding.chipCar.id to RemoteIcon.CAR,
            binding.chipLight.id to RemoteIcon.LIGHT,
            binding.chipLock.id to RemoteIcon.LOCK,
            binding.chipFan.id to RemoteIcon.FAN,
            binding.chipGeneric.id to RemoteIcon.GENERIC
        )

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_add_device_title)
            .setView(binding.root)
            .setPositiveButton(R.string.dialog_save) { _, _ ->
                val name = binding.nameInput.text?.toString()?.trim().orEmpty()
                val checkedId = binding.iconChipGroup.checkedChipId
                val iconKey = chipToIcon[checkedId]?.name ?: RemoteIcon.GENERIC.name
                onSave(name.ifEmpty { "Unnamed signal" }, iconKey)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()
    }
}
