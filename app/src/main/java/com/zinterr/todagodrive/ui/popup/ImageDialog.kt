package com.zinterr.todagodrive.ui.popup

import android.annotation.SuppressLint
import android.app.*
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.squareup.picasso.Picasso
import androidx.core.graphics.drawable.toDrawable
import com.zinterr.todagodrive.databinding.DialogImageBinding

@SuppressLint("SetTextI18n")
class ImageDialog(
    private val title: String,
    private val url: String
)
    : DialogFragment()
{
    private lateinit var binding: DialogImageBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogImageBinding.inflate(layoutInflater)
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(binding.root)
        val dialog = builder.create()

        Picasso.get().load(url).into(binding.image)
        binding.title.text = title

        if (dialog.window != null) dialog.window!!.setBackgroundDrawable(0.toDrawable())
        return dialog
    }
}