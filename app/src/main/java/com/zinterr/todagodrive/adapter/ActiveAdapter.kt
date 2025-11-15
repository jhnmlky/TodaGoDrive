package com.zinterr.todagodrive.adapter

import android.view.*
import androidx.recyclerview.widget.RecyclerView
import com.zinterr.todagodrive.databinding.ItemDriverBinding
import com.zinterr.todagodrive.model.RideDriver
import com.zinterr.todagodrive.util.ActiveClickListener
import com.zinterr.todagodrive.viewholder.ActiveViewHolder

class ActiveAdapter(
    private val users: ArrayList<RideDriver>,
    private val clickListener: ActiveClickListener
) :
    RecyclerView.Adapter<ActiveViewHolder>()
{
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ActiveViewHolder {
        val from = LayoutInflater.from(parent.context)
        val binding = ItemDriverBinding.inflate(from, parent, false)
        return ActiveViewHolder(binding, clickListener)
    }

    override fun onBindViewHolder(holder: ActiveViewHolder, position: Int) {
        holder.bindUser(users[position])
    }

    override fun getItemCount(): Int = users.size

}