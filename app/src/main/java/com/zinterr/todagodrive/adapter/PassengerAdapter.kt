package com.zinterr.todagodrive.adapter

import android.view.*
import androidx.recyclerview.widget.RecyclerView
import com.zinterr.todagodrive.databinding.ItemPassengerBinding
import com.zinterr.todagodrive.model.Commuter
import com.zinterr.todagodrive.util.PassengerCallClickListener
import com.zinterr.todagodrive.viewholder.PassengerViewHolder

class PassengerAdapter(
    private val passengers: ArrayList<Commuter>,
    private val clickListener: PassengerCallClickListener
) :
    RecyclerView.Adapter<PassengerViewHolder>()
{
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PassengerViewHolder {
        val from = LayoutInflater.from(parent.context)
        val binding = ItemPassengerBinding.inflate(from, parent, false)
        return PassengerViewHolder(binding, clickListener)
    }
    override fun onBindViewHolder(holder: PassengerViewHolder, position: Int) {
        holder.bindPassenger(passengers[position])
    }
    override fun getItemCount(): Int = passengers.size
}