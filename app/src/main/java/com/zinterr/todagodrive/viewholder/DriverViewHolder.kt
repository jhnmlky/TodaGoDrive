package com.zinterr.todagodrive.viewholder

import android.annotation.SuppressLint
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.zinterr.todagodrive.R
import com.zinterr.todagodrive.databinding.ItemDriverBinding
import com.zinterr.todagodrive.model.Global.setOnDebouncedClickListener
import com.zinterr.todagodrive.model.*
import com.zinterr.todagodrive.util.DriverClickListener

@SuppressLint("SetTextI18n")
class DriverViewHolder(
    private var binding: ItemDriverBinding,
    private val clickListener: DriverClickListener
) :
    RecyclerView.ViewHolder(binding.root)
{
    fun bindUser(user: User) {
        if (user.profile.isNullOrBlank()) binding.driverProfile.setImageResource(R.drawable.tricycle)
        else Picasso.get()
            .load(user.profile)
            .placeholder(R.drawable.tricycle)
            .into(binding.driverProfile)
        binding.driverName.text = user.name
        binding.driverUnverified.visibility = if (user.verified == false) View.VISIBLE else View.GONE
        binding.driverStars.rating = user.rating!!/user.rates!!.toFloat()
        binding.driverRate.text = "${if ((user.rating!! / user.rates!!.toFloat()).isNaN()) 0F else "%.2f".format(user.rating!!/user.rates!!.toFloat())}"
        binding.driverPlate.text = user.plate
        binding.driverStatus.apply {
            text = user.status
            visibility = View.VISIBLE
        }
        binding.cvDriver.setOnDebouncedClickListener {
            clickListener.onDriverClick(user)
        }
    }
}