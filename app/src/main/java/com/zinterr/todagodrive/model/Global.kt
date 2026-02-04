package com.zinterr.todagodrive.model

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.location.*
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.*
import androidx.core.app.ActivityCompat
import androidx.core.content.*
import androidx.fragment.app.Fragment
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import com.zinterr.todagodrive.network.RetrofitClient
import retrofit2.*
import java.io.*

object Global {

    const val VERSION = "1.0.0"
    private var lastClickTime = 0L
    var historyPrev: String? = null
    var navPosition: Int = 0
    var app: App? = null
    var tabPosition: Int = 0
    var searching: Boolean = false
    var activeUser: RideDriver? = null
    val MINIMAL = listOf(
        "routes.distanceMeters",
        "routes.duration",
        "routes.staticDuration",
        "routes.legs.startLocation",
        "routes.legs.endLocation"
    ).joinToString(",")
    val STANDARD = listOf(
        "routes.distanceMeters",
        "routes.duration",
        "routes.staticDuration",
        "routes.polyline",
        "routes.viewport",
        "routes.legs.startLocation",
        "routes.legs.endLocation"
    ).joinToString(",")

    fun timeTo12(time: String): String {
        val (hours, minutes) = time.split(":").map(String::toInt)
        val period = if (hours < 12) "AM" else "PM"
        val hour = (if (hours % 12 == 0) 12 else hours % 12).toString().padStart(2, '0')
        return "$hour:${minutes.toString().padStart(2, '0')} $period"
    }

    fun LatLngLiteral.toLatLng(): LatLng = LatLng(lat, lng)

    fun String.toLatLng(): LatLng = LatLng(substringBefore(',').toDouble(), substringAfter(',').toDouble())

    fun LatLng.toLatLngString(): String = "$latitude,$longitude"

    fun Location.toLatLngString(): String = "$latitude,$longitude"

    fun isNotificationDisabled(context: Context): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    fun View.setOnDebouncedClickListener(interval: Long = 750, onClick: (View) -> Unit) {
        setOnClickListener {
            val currentTime = SystemClock.elapsedRealtime()
            if (currentTime - lastClickTime >= interval) {
                lastClickTime = currentTime
                onClick(it)
            }
        }
    }

    fun createImageUri(context: Context, name: String): Uri {
        val file = File(context.cacheDir, "$name.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    fun getLocation(context: Context, onComplete: (LatLng?) -> Unit) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        if (ActivityCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            onComplete(null)
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) onComplete(LatLng(location.latitude, location.longitude))
            else {
                val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 0)
                    .setWaitForAccurateLocation(true)
                    .setMaxUpdates(1)
                    .setMinUpdateIntervalMillis(0)
                    .build()
                fusedLocationClient.requestLocationUpdates(request,
                    object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            fusedLocationClient.removeLocationUpdates(this)
                            val loc = result.lastLocation
                            if (loc != null) onComplete(LatLng(loc.latitude, loc.longitude))
                            else onComplete(null)
                        }
                    }, Looper.getMainLooper())
            }
        }.addOnFailureListener {
            onComplete(null)
        }
    }

    fun GeocodeResponse.toLocalAddress(): LocalAddress {
        val results = this.results
        var province: String? = null
        var city: String? = null
        var locality: String? = null
        var extra: String? = null
        results.forEach { result ->
            result.addressComponents.forEach { comp ->
                when {
                    province == null && comp.types.contains("administrative_area_level_2") -> province = comp.longName
                    city == null && comp.types.contains("locality") -> city = comp.longName
                    locality == null && comp.types.contains("sublocality") || comp.types.contains("sublocality_level_2") -> locality = comp.longName
                    extra == null && comp.types.contains("route") -> extra = comp.longName
                }
            }
        }
        return LocalAddress(province = province, city = city,
            locality = locality, extra = extra)
    }

    fun getAddressFromLocation(latLng: LatLng, key: String, onComplete: (LocalAddress?) -> Unit) {
        RetrofitClient.geocodeService.reverseGeocode(latLng.toLatLngString(), key)
            .enqueue(object : Callback<GeocodeResponse> {
                override fun onResponse(call: Call<GeocodeResponse?>, response: Response<GeocodeResponse?>) {
                    if (response.isSuccessful) {
                        val address = response.body()?.toLocalAddress()!!
                        onComplete(address)
                    } else onComplete(null)
                }
                override fun onFailure(call: Call<GeocodeResponse?>, t: Throwable) {
                    onComplete(null)
                }
            })
    }

    fun checkGPS(context: Context,
        locationSettingsLauncher: ActivityResultLauncher<IntentSenderRequest>,
        onComplete: (String?) -> Unit
    ) {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 0).build()
        val locationSettingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .build()
        val settingsClient = LocationServices.getSettingsClient(context)
        settingsClient.checkLocationSettings(locationSettingsRequest)
            .addOnSuccessListener {
                onComplete(null)
            }
            .addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    try {
                        val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution).build()
                        locationSettingsLauncher.launch(intentSenderRequest)
                    } catch (sendEx: IntentSender.SendIntentException) {
                        Log.e("Location", "Error prompting GPS enable: ${sendEx.message}")
                        onComplete("Location is not available")
                    }
                }
            }
    }

    fun getUriBytes(context: Context, uri: Uri, maxSize: Int): ByteArray {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri), null, options)
        options.inSampleSize = sizing(options)
        options.inJustDecodeBounds = false
        val inputStream = context.contentResolver.openInputStream(uri) ?: return ByteArray(0)
        val bitmap = BitmapFactory.decodeStream(inputStream, null, options) ?: return ByteArray(0)
        val format = if (options.outMimeType == "image/png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val outputStream = ByteArrayOutputStream()
        var quality = 100
        do {
            outputStream.reset()
            bitmap.compress(format, quality, outputStream)
            quality -= 5
        } while (outputStream.toByteArray().size > maxSize && quality > 0)
        return outputStream.toByteArray()
    }

    private fun sizing(options: BitmapFactory.Options): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > 1080 || width > 1080) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= 1080 && (halfWidth / inSampleSize) >= 1080)
                inSampleSize *= 2
        }
        return inSampleSize
    }

    fun Fragment.hideKeyboard() {
        view?.let { activity?.hideKeyboard(it) }
    }

    fun Activity.hideKeyboard() {
        val view = currentFocus ?: window.decorView
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun Context.hideKeyboard(view: View) {
        val imm = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}