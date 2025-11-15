package com.zinterr.todagodrive.adapter

import android.view.*
import androidx.recyclerview.widget.RecyclerView
import com.zinterr.todagodrive.databinding.ItemDriverBinding
import com.zinterr.todagodrive.model.User
import com.zinterr.todagodrive.util.DriverClickListener
import com.zinterr.todagodrive.viewholder.DriverViewHolder

class DriverAdapter(
    private val users: ArrayList<User>,
    private val clickListener: DriverClickListener
) :
    RecyclerView.Adapter<DriverViewHolder>()
{
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): DriverViewHolder {
        val from = LayoutInflater.from(parent.context)
        val binding = ItemDriverBinding.inflate(from, parent, false)
        return DriverViewHolder(binding, clickListener)
    }

    override fun onBindViewHolder(holder: DriverViewHolder, position: Int) {
        holder.bindUser(users[position])
    }

    override fun getItemCount(): Int = users.size

}