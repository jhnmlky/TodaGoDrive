package com.zinterr.todagodrive.viewmodel

import androidx.lifecycle.*

class BusyViewModel : ViewModel() {

    private val _isBusy = MutableLiveData(false)
    val isBusy: LiveData<Boolean> get() = _isBusy

    fun setBusy(value: Boolean) {
        _isBusy.value = value
    }
}