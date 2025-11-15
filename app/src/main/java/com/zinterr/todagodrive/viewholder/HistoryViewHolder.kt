package com.zinterr.todagodrive.viewholder

import android.annotation.SuppressLint
import android.view.View
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.zinterr.todagodrive.databinding.ItemHistoryBinding
import com.zinterr.todagodrive.model.*
import com.zinterr.todagodrive.model.Global.timeTo12
import com.zinterr.todagodrive.util.HistoryClickListener
import kotlin.apply

@SuppressLint("SetTextI18n")
class HistoryViewHolder(
    private var binding: ItemHistoryBinding,
    private val clickListener: HistoryClickListener,
    private val isAssoc: Boolean
) :
    RecyclerView.ViewHolder(binding.root)
{
    lateinit var id: String

    fun bindHistory(history: History, showDate: Boolean) {
        id = history.uid!!
        if (showDate) binding.historyDate.apply {
            text = history.dateTime?.substringBefore(' ')
            visibility = View.VISIBLE
        }
        binding.historyTime.text = timeTo12(history.dateTime?.substringAfter(' ')!!)
        binding.historyUID.text = history.uid
        if (isAssoc) binding.historyDriver.apply {
            text = history.driver
            visibility = View.VISIBLE
        }
        binding.historyType.text = "${if (history.solo == true) "SOLO" else "POOL"} RIDE"
        binding.historyPassenger.text = "${history.passenger}"
        if (history.solo == false) binding.historyOriginLabel.text = "Origins:"
        binding.historyOrigin.text = if (history.solo == false)
            history.start?.replace("|", "\n") else history.start
        if (history.solo == false) binding.historyDestination.text = "Destinations:"
        binding.historyDestination.text = if (history.solo == false)
            history.end?.replace("|", "\n") else history.end
        binding.historyPrice.text = "₱ ${history.price}.00"
        binding.cvHistory.setOnClickListener {
            if (Global.historyPrev == id) {
                binding.content.apply {
                    visibility = if (isVisible) View.GONE
                    else View.VISIBLE
                }
            } else {
                if (Global.historyPrev != null) resetCard(Global.historyPrev!!)
                binding.content.visibility = View.VISIBLE
                Global.historyPrev = id
            }
        }
    }

    private fun resetCard(id: String) {
        val previous = clickListener.getViewHolder(id)
        for (viewHolder in previous)
            viewHolder.binding.content.visibility = View.GONE
    }
}