package com.zinterr.todagodrive.ui.popup

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.core.graphics.drawable.toDrawable
import com.google.android.gms.maps.model.LatLng
import com.zinterr.todagodrive.databinding.DialogRoutePickerBinding
import com.zinterr.todagodrive.model.*
import com.zinterr.todagodrive.model.Global.setOnDebouncedClickListener
import com.zinterr.todagodrive.model.Global.toLocalAddress
import com.zinterr.todagodrive.network.RetrofitClient
import com.zinterr.todagodrive.util.*
import retrofit2.*

@SuppressLint("SetTextI18n")
class RoutePickerDialog(
    private val apiKey: String,
    private val onComplete: (LatLng, LocalAddress, LatLng?, LocalAddress?) -> Unit
) : DialogFragment() {

    private lateinit var binding: DialogRoutePickerBinding
    private var startAddress: LocalAddress? = null
    private var endAddress: LocalAddress? = null
    private lateinit var dialog: AlertDialog
    private var textView: TextView? = null
    private var start: LatLng? = null
    private var end: LatLng? = null
    private val getLocation = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) showData(result.data) }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogRoutePickerBinding.inflate(layoutInflater)
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(binding.root)
        dialog = builder.create()
        dialog.setCanceledOnTouchOutside(false)

        binding.locationPickerStart.setOnDebouncedClickListener {
            getAddress(binding.locationStart)
        }

        binding.locationPickerEnd.setOnDebouncedClickListener {
            getAddress(binding.locationEnd)
        }

        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnConfirm.setOnDebouncedClickListener {
            changeRoute()
        }

        if (dialog.window != null) dialog.window!!.setBackgroundDrawable(0.toDrawable())
        return dialog
    }

    private fun changeRoute() {
        if (start != null && startAddress != null) {
            onComplete(start!!, startAddress!!, end, endAddress)
            dialog.dismiss()
        } else snackBar(binding.root, "Please fulfill all required information")
    }

    private fun getAddress(view: TextView) {
        textView = view
        val intent = Intent(requireContext(), LocationPickerActivity::class.java)
        getLocation.launch(intent)
    }

    private fun showData(data: Intent?) {
        val latitude = data?.getDoubleExtra("LATITUDE", 0.0)
        val longitude = data?.getDoubleExtra("LONGITUDE", 0.0)
        if (textView == binding.locationStart) start = LatLng(latitude!!, longitude!!)
        if (textView == binding.locationEnd) end = LatLng(latitude!!, longitude!!)
        RetrofitClient.geocodeService.reverseGeocode("$latitude,$longitude", apiKey)
            .enqueue(object : Callback<GeocodeResponse> {
                override fun onResponse(call: Call<GeocodeResponse?>, response: Response<GeocodeResponse?>) {
                    if (response.isSuccessful) {
                        val address = response.body()?.toLocalAddress()!!
                        if (textView == binding.locationStart) startAddress = address
                        if (textView == binding.locationEnd) endAddress = address
                        textView?.text = (if (address.extra != null) "${address.extra}, " else "") +
                                (if (address.locality != null) "${address.locality}, " else "") +
                                (if (address.city != null) "${address.city}, " else "") +
                                (address.province ?: "")
                    } else snackBar(binding.root, "Error: ${response.errorBody()}")
                }
                override fun onFailure(call: Call<GeocodeResponse?>, t: Throwable) {}
            })
    }
}