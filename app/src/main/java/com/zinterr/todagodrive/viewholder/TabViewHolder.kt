package com.zinterr.todagodrive.viewholder

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.zinterr.todagodrive.databinding.ItemTabBinding

class TabViewHolder(
    private var binding: ItemTabBinding
) :
    RecyclerView.ViewHolder(binding.root)
{
    fun bindItem(rv: View) {
        binding.pageContainer.addView(rv)
    }
}
