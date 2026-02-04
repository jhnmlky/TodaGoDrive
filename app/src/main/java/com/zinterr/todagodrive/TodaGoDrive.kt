package com.zinterr.todagodrive

import android.app.Application
import androidx.lifecycle.*
import com.zinterr.todagodrive.util.LocationService

class TodaGoDrive : Application(), ViewModelStoreOwner, DefaultLifecycleObserver {

    private val appViewModelStore = ViewModelStore()

    override val viewModelStore: ViewModelStore
        get() = appViewModelStore

    override fun onCreate() {
        super<Application>.onCreate()
        getSharedPreferences("prefs", MODE_PRIVATE)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        LocationService.setAppInForeground(true)
    }

    override fun onStop(owner: LifecycleOwner) {
        LocationService.setAppInForeground(false)
    }

    override fun onTerminate() {
        super.onTerminate()
        appViewModelStore.clear()
    }
}