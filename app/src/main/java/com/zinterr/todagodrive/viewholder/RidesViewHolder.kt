package com.zinterr.todagodrive.viewholder

import android.annotation.SuppressLint
import androidx.recyclerview.widget.RecyclerView
import com.zinterr.todagodrive.databinding.ItemRideBinding
import com.zinterr.todagodrive.model.Global.setOnDebouncedClickListener
import com.zinterr.todagodrive.model.Ride
import com.zinterr.todagodrive.util.RideClickListener

@SuppressLint("SetTextI18n")
class RidesViewHolder(
    private var binding: ItemRideBinding,
    private val clickListener: RideClickListener
) :
    RecyclerView.ViewHolder(binding.root)
{
    fun bindRide(ride: Ride) {
        binding.start.text = ride.commuter?.values?.firstOrNull()?.startAddress
        binding.end.text = ride.commuter?.values?.firstOrNull()?.endAddress
        binding.trip.text = ride.commuter?.values?.firstOrNull()?.let {
            "${"%.2f".format(it.distance!! / 1000F)}km ● ${formatDuration(it.duration!!.toLong())}"
        }
        binding.weight.text = "${if (ride.solo == true) "SOLO" else "SHARE"} ● ${when (ride.weight) {
            1 -> "Handcarry"
            2 -> "Small"
            else -> "Large"
        }} ● ${ride.passenger}"
        binding.btnTake.setOnDebouncedClickListener {
            clickListener.onRideClick(ride.uid!!)
        }
    }

    private fun formatDuration(seconds: Long): String {
        val hour = seconds / 3600
        val minute = (seconds % 3600) / 60
        val secs = seconds % 60
        return when {
            hour > 0 -> "%d hr %02d min %02d sec".format(hour, minute, secs)
            minute > 0 -> "%d min %02d sec".format(minute, secs)
            else -> "%d sec".format(secs)
        }
    }
}