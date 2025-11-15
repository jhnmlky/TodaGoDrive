package com.zinterr.todagodrive.adapter

import android.view.*
import androidx.recyclerview.widget.RecyclerView
import com.zinterr.todagodrive.databinding.ItemRideBinding
import com.zinterr.todagodrive.model.Ride
import com.zinterr.todagodrive.util.RideClickListener
import com.zinterr.todagodrive.viewholder.RidesViewHolder

class RideAdapter(
    private val rides: ArrayList<Ride>,
    private val clickListener: RideClickListener
) :
    RecyclerView.Adapter<RidesViewHolder>()
{
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RidesViewHolder {
        val from = LayoutInflater.from(parent.context)
        val binding = ItemRideBinding.inflate(from, parent, false)
        return RidesViewHolder(binding, clickListener)
    }

    override fun onBindViewHolder(holder: RidesViewHolder, position: Int) {
        holder.bindRide(rides[position])
    }

    override fun getItemCount(): Int = rides.size
}