package com.zinterr.todagodrive.ui.popup

import android.app.*
import android.os.Bundle
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import com.zinterr.todagodrive.databinding.DialogPlateBinding
import com.zinterr.todagodrive.model.Global.setOnDebouncedClickListener

class PlateDialog(
    private val plate: String,
    private val onComplete: (Boolean) -> Unit
) : DialogFragment() {

    private lateinit var binding: DialogPlateBinding
    private lateinit var dialog: AlertDialog

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogPlateBinding.inflate(layoutInflater)
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(binding.root)
        dialog = builder.create()
        dialog.setCanceledOnTouchOutside(false)

        binding.plate.text = plate

        binding.btnNegative.setOnClickListener {
            dialog.dismiss()
            onComplete(false)
        }

        binding.btnPositive.setOnDebouncedClickListener {
            dialog.dismiss()
            onComplete(true)
        }

        if (dialog.window != null) dialog.window!!.setBackgroundDrawable(0.toDrawable())
        return dialog
    }
}