package com.zinterr.todagodrive.util

import com.zinterr.todagodrive.model.*
import com.zinterr.todagodrive.viewholder.HistoryViewHolder

interface DriverClickListener {
    fun onDriverClick(user: User)
}

interface ActiveClickListener {
    fun onActiveClick(user: RideDriver)
}

interface HistoryClickListener {
    fun getViewHolder(id: String): List<HistoryViewHolder>
}

interface PassengerCallClickListener {
    fun onCallClick(phone: String)
    fun onChatClick(uid: String)
}

interface RideClickListener {
    fun onRideClick(rideUID: String)
}