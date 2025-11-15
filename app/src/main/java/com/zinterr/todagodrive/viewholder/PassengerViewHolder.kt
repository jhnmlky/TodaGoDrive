package com.zinterr.todagodrive.viewholder

import android.annotation.SuppressLint
import androidx.recyclerview.widget.RecyclerView
import com.zinterr.todagodrive.databinding.ItemPassengerBinding
import com.zinterr.todagodrive.model.Commuter
import com.zinterr.todagodrive.R
import com.zinterr.todagodrive.model.Global.setOnDebouncedClickListener
import com.zinterr.todagodrive.util.PassengerCallClickListener

@SuppressLint("SetTextI18n")
class PassengerViewHolder(
    private var binding: ItemPassengerBinding,
    private val clickListener: PassengerCallClickListener
) :
    RecyclerView.ViewHolder(binding.root)
{
    fun bindPassenger(commuter: Commuter) {
        binding.passengerProfile.setImageResource(R.drawable.commuter)
        binding.passengerName.text = commuter.name
        binding.passengerStars.rating = commuter.rate!!.toFloat()
        binding.passengerRate.text = "${commuter.rate.toFloat()}"
        binding.passengerWeight.text = "${when (commuter.weight) {
            1 -> "Handcarry"
            2 -> "Small"
            else -> "Large"
        }} ● ${commuter.passenger}${if (commuter.price != null) " ● ₱${commuter.price}.00" else ""}"
        binding.passengerStatus.text = when {
            commuter.status!!.contains("DRIVING") -> "On board"
            else -> "Waiting"
        }
        binding.btnCall.setOnDebouncedClickListener {
            clickListener.onCallClick(commuter.phone!!)
        }
        binding.btnChat.setOnDebouncedClickListener {
            clickListener.onChatClick(commuter.uid!!)
        }
    }
}