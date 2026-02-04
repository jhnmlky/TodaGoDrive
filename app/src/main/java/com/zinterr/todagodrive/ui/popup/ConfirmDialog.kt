package com.zinterr.todagodrive.ui.popup

import android.annotation.SuppressLint
import android.app.*
import android.os.*
import android.view.*
import androidx.fragment.app.DialogFragment
import java.util.Locale
import androidx.core.graphics.drawable.toDrawable
import com.squareup.picasso.Picasso
import com.zinterr.todagodrive.databinding.DialogConfirmBinding
import com.zinterr.todagodrive.model.Global.setOnDebouncedClickListener

@SuppressLint("SetTextI18n")
class ConfirmDialog(
    private val title: String,
    private val message: String,
    private val positive: String,
    private val negative: String? = null,
    private val isTimer: Boolean? = null,
    private val images: Pair<String?, String?>? = null,
    private val onComplete: (Boolean) -> Unit
): DialogFragment() {

    private lateinit var binding: DialogConfirmBinding
    private var imageDialog: ImageDialog? = null
    private lateinit var dialog: AlertDialog
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    private var s = 11

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogConfirmBinding.inflate(layoutInflater)
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(binding.root)
        dialog = builder.create()
        dialog.setCanceledOnTouchOutside(false)

        binding.title.text = title.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        if (images != null) {
            binding.images.visibility = View.VISIBLE
            Picasso.get().load(images.first).into(binding.image1)
            Picasso.get().load(images.second).into(binding.image2)
            if (images.second != null) binding.image2.visibility = View.VISIBLE
        }
        binding.message.text = message
        binding.btnPositive.text = positive
        if (negative != null) binding.btnNegative.apply {
            text = negative
            visibility = View.VISIBLE
        }

        if (isTimer == true) {
            runnable = object : Runnable {
                override fun run() {
                    s -= 1
                    if (s >= 1) {
                        binding.btnPositive.text = "$positive ($s)"
                        handler.postDelayed(this, 1000L)
                    } else if (dialog.isShowing) finish(true)
                }
            }
            runnable?.let { handler.post(it) }
        }

        binding.image1.setOnDebouncedClickListener {
            openImage(images?.first)
        }

        binding.image2.setOnDebouncedClickListener {
            openImage(images?.second)
        }

        binding.btnNegative.setOnClickListener {
            finish(false)
        }

        binding.btnPositive.setOnDebouncedClickListener {
            finish(true)
        }

        if (dialog.window != null) dialog.window!!.setBackgroundDrawable(0.toDrawable())
        return dialog
    }

    override fun onDestroy() {
        super.onDestroy()
        if (imageDialog != null) if (imageDialog!!.isAdded) imageDialog!!.dismiss()
    }

    private fun openImage(url: String?) {
        imageDialog = ImageDialog("Image Document", url!!)
        imageDialog!!.show(childFragmentManager, "ImageDialog")
    }

    private fun finish(confirm: Boolean) {
        runnable?.let { handler.removeCallbacks(it) }
        dialog.dismiss()
        onComplete(confirm)
    }
}