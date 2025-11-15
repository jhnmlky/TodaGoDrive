package com.zinterr.todagodrive

import android.app.Application
import androidx.lifecycle.*
import com.zinterr.todagodrive.util.LocationService

class TodaGoDrive : Application(), DefaultLifecycleObserver {

    override fun onCreate() {
        super<Application>.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        LocationService.setAppInForeground(true)
    }

    override fun onStop(owner: LifecycleOwner) {
        LocationService.setAppInForeground(false)
    }
}