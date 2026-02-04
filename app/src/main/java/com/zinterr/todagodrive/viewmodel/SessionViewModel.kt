package com.zinterr.todagodrive.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.google.android.gms.maps.model.LatLng
import com.zinterr.todagodrive.model.*
import kotlinx.coroutines.flow.MutableStateFlow

class SessionViewModel(app: Application): AndroidViewModel(app) {

    private val _profiledUser = MutableStateFlow<User?>(null)
    val profiledUser = _profiledUser
    private val _account = MutableStateFlow<Account?>(null)
    val account = _account
    private val _user = MutableStateFlow<User?>(null)
    val user = _user
    private val _city = MutableStateFlow<String?>(null)
    val city = _city
    private val _key = MutableStateFlow<String?>(null)
    val key = _key
    private val _current = MutableStateFlow<LatLng?>(null)
    val current = _current
    private val _driver = MutableStateFlow<RideDriver?>(null)
    val driver = _driver
    private val _matrix = MutableStateFlow<Matrix?>(null)
    val matrix = _matrix
    private val _deviceID = MutableStateFlow<String?>(null)
    val deviceID = _deviceID
    private val _fee = MutableStateFlow<Double?>(null)
    val fee = _fee

    fun setProfiledUser(value: User?) {
        _profiledUser.value = value
    }

    fun setAccount(value: Account?) {
        _account.value = value
    }

    fun setUser(value: User?) {
        _user.value = value
    }

    fun setCity(value: String?) {
        _city.value = value
    }

    fun setKey(value: String?) {
        _key.value = value
    }

    fun setCurrent(value: LatLng?) {
        _current.value = value
    }

    fun setDriver(value: RideDriver?) {
        _driver.value = value
    }

    fun setMatrix(value: Matrix?) {
        _matrix.value = value
    }

    fun setDeviceID(value: String?) {
        _deviceID.value = value
    }

    fun setFee(value: Double?) {
        _fee.value = value
    }
}