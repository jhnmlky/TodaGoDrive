package com.zinterr.todagodrive.viewholder

import android.annotation.SuppressLint
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.zinterr.todagodrive.R
import com.zinterr.todagodrive.databinding.ItemDriverBinding
import com.zinterr.todagodrive.model.Global.setOnDebouncedClickListener
import com.zinterr.todagodrive.model.*
import com.zinterr.todagodrive.util.ActiveClickListener

@SuppressLint("SetTextI18n")
class ActiveViewHolder(
    private var binding: ItemDriverBinding,
    private val clickListener: ActiveClickListener
) :
    RecyclerView.ViewHolder(binding.root)
{
    fun bindUser(user: RideDriver) {
        binding.driverStars.visibility = View.GONE
        if (user.profile.isNullOrBlank()) binding.driverProfile.setImageResource(R.drawable.tricycle)
        else Picasso.get()
            .load(user.profile)
            .placeholder(R.drawable.tricycle)
            .into(binding.driverProfile)
        binding.driverName.text = user.name
        binding.driverPlate.text = user.plate
        binding.cvDriver.setOnDebouncedClickListener {
            clickListener.onActiveClick(user)
        }
    }
}