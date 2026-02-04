package com.zinterr.todagodrive.util

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Location
import android.os.*
import androidx.core.app.*
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.*
import com.google.firebase.remoteconfig.remoteConfig
import com.zinterr.todagodrive.LoginActivity
import com.zinterr.todagodrive.model.Global.getLocation
import com.zinterr.todagodrive.model.Global.toLatLngString
import com.zinterr.todagodrive.model.Global.toLocalAddress
import com.zinterr.todagodrive.model.*
import com.zinterr.todagodrive.R
import com.zinterr.todagodrive.model.Global.toLatLng
import com.zinterr.todagodrive.network.RetrofitClient
import retrofit2.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class LocationService: Service() {

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var geocodeResponse: Callback<GeocodeResponse>? = null
    private var activeEventListener: ValueEventListener? = null
    private var rideEventListener: ValueEventListener? = null
    private var locationCallback: LocationCallback? = null
    private var geocodeCall: Call<GeocodeResponse>? = null
    private val uid = Firebase.auth.currentUser?.uid!!
    private val remoteConfig = Firebase.remoteConfig
    private var activeRef: DatabaseReference? = null
    private val dbRef = Firebase.database.reference
    private var rideRef: DatabaseReference? = null
    private var currentRideUID: String? = null
    private var currentCity: String? = null
    private var driver: RideDriver? = null
    private var current: LatLng? = null
    private var key: String? = null

    companion object {
        private var instance: LocationService? = null
        lateinit var prefs: SharedPreferences
        private var appInForeground = true
        const val EXTRA_CITY = "CITY"
        const val EXTRA_RIDE_UID = "RIDE_UID"
        const val NOTIFICATION_ID = 1001

        fun bind(service: LocationService) {
            instance = service
        }

        fun startService(context: Context, city: String? = null, rideUID: String? = null) {
            prefs = context.getSharedPreferences("session_prefs", MODE_PRIVATE)
            prefs.edit().putBoolean("appInForeground", true).apply()
            if (instance != null) instance?.stopLocationService()
            Handler(Looper.getMainLooper()).postDelayed({
                val intent = Intent(context, LocationService::class.java).apply {
                    putExtra(EXTRA_CITY, city)
                    putExtra(EXTRA_RIDE_UID, rideUID)
                }
                ContextCompat.startForegroundService(context, intent)
            }, 0)
        }

        fun stopService() {
            instance?.stopLocationService()
        }

        fun setAppInForeground(foreground: Boolean) {
            if (instance != null) {
                appInForeground = foreground
                prefs.edit().putBoolean("appInForeground", foreground).apply()
                if (!appInForeground) instance?.let {
                    instance?.updateNotification()
                    it.currentCity?.let { city ->
                        it.startLocationUpdates(city)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        bind(this)
        createNotificationChannel()
        fetchAPI()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val city = intent?.getStringExtra(EXTRA_CITY)
        val rideUID = intent?.getStringExtra(EXTRA_RIDE_UID)
        setupPresence()
        if (city != null) {
            currentCity = city
            currentRideUID = rideUID
            getDriver()
            startLocationUpdates(currentCity!!, currentRideUID)
        } else checkActiveRide { city, ride ->
            currentCity = city
            currentRideUID = ride?.uid
            getDriver()
            startLocationUpdates(currentCity!!, currentRideUID)
        }
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    private fun getDriver() {
        dbRef.child("Ride/$currentCity/Driver/$uid")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val rideDriver = snapshot.getValue(RideDriver::class.java)!!
                        driver = rideDriver
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    getDriver()
                }
            })
    }

    private fun setupPresence() {
        val presenceRef = Firebase.database.getReference(".info/connected")
        presenceRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    val userRef = dbRef.child("Ride/$currentCity/Driver/$uid/active")
                    userRef.onDisconnect().setValue(false)
                    userRef.setValue(true)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun checkRides() {
        dbRef.child("Ride/$currentCity/Rides").addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val rides = arrayListOf<Ride>()
                    if (snapshot.exists()) {
                        for (item in snapshot.children) {
                            val ride = item.getValue(Ride::class.java)!!
                            rides.add(ride)
                        }
                        if (rides.isNotEmpty()) {
                            rides.firstOrNull { it.driver == null && isRideAvailable(it) }?.let {
                                if (!appInForeground) notifyRide("TodaGo has found a ride",
                                    "A ride is available for you. Hurry and check it out now.")
                            }
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun isRideAvailable(ride: Ride): Boolean {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        if (driver != null) {
            if (driver?.dateTime != null ) {
                val driverDateTime = LocalDateTime.parse(driver?.dateTime, formatter)
                val driverTimeDiff = ChronoUnit.SECONDS.between(driverDateTime, LocalDateTime.now())
                if (driverTimeDiff >= 30) return checkDistance(ride, formatter)
            } else return checkDistance(ride, formatter)
        }
        return false
    }

    private fun checkDistance(ride: Ride, formatter: DateTimeFormatter): Boolean {
        val dateTime = LocalDateTime.parse(ride.dateTime, formatter)
        val timeDiff = ChronoUnit.SECONDS.between(dateTime, LocalDateTime.now())
        val start = isClosest(ArrayList(ride.commuter!!.values)).start?.toLatLng()
        val locDiff = distanceBetween(start!!, current!!)
        val distance = listOf(1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144)
        if (timeDiff >= 300) return true
        for (i in 1..30) {
            val timeLimit = i * 10L
            val index = (i-1).coerceAtMost(distance.lastIndex)
            val distanceLimit = distance[index] * 100F
            if (timeDiff < timeLimit) return locDiff < distanceLimit
        }
        return false
    }

    private fun isClosest(commuters: List<Commuter>): Commuter= commuters.minBy {
        distanceBetween(it.start!!.toLatLng(), current!!)
    }

    private fun distanceBetween(p1: LatLng, p2: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(p1.latitude, p1.longitude,
            p2.latitude, p2.longitude, results)
        return results[0]
    }

    private fun checkActiveRide(onComplete: (String?, Ride?) -> Unit) {
        getLocation(this) { latLng ->
            if (latLng != null) {
                geocodeResponse = object : Callback<GeocodeResponse> {
                    override fun onResponse(call: Call<GeocodeResponse?>, response: Response<GeocodeResponse?>) {
                        if (response.isSuccessful) {
                            val city = response.body()?.toLocalAddress()!!.city!!
                            activeEventListener = object : ValueEventListener {
                                override fun onDataChange(snapshot: DataSnapshot) {
                                    var currentRide: Ride? = null
                                    if (snapshot.exists()) {
                                        snapshot.children.forEach { item ->
                                            val ride = item.getValue(Ride::class.java)!!
                                            if (ride.driver?.uid == uid) {
                                                currentRide = ride
                                                return@forEach
                                            }
                                        }
                                        if (currentRide != null) onComplete(city, currentRide)
                                        else onComplete(city, null)
                                    } else onComplete(city, null)
                                }
                                override fun onCancelled(error: DatabaseError) {}
                            }
                            activeRef = dbRef.child("Ride/$city/Rides")
                            activeRef?.addListenerForSingleValueEvent(activeEventListener!!)
                        }
                    }
                    override fun onFailure(call: Call<GeocodeResponse?>, t: Throwable) {}
                }
                if (key == null) fetchAPI {
                    geocodeCall = RetrofitClient.geocodeService.reverseGeocode(latLng.toLatLngString(), key!!)
                    geocodeCall?.enqueue(geocodeResponse!!)
                } else {
                    geocodeCall = RetrofitClient.geocodeService.reverseGeocode(latLng.toLatLngString(), key!!)
                    geocodeCall?.enqueue(geocodeResponse!!)
                }
            }
        }
    }

    private fun stopLocationService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLocationUpdates(city: String, rideUID: String? = null) {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,
            5000).setMinUpdateIntervalMillis(3000).build()
        removeLocationUpdates()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                val location = result.lastLocation ?: return
                current = location.toLatLngString().toLatLng()
                if (uid == Firebase.auth.currentUser?.uid) {
                    dbRef.child("Ride/$city/${if (rideUID != null) "Rides/$rideUID/driver/current"
                    else "Driver/$uid/current"}").setValue(location.toLatLngString())
                    if (!appInForeground) {
                        if (rideUID == null) checkRides()
                        else if (rideRef == null && rideEventListener == null) startRideMonitoring()
                    } else {
                        updateNotification()
                        removeRideListener()
                    }
                } else stopLocationService()
            }
        }
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            fusedLocationClient?.requestLocationUpdates(
                request, locationCallback!!, Looper.getMainLooper())
        }
    }

    private fun startRideMonitoring() {
        rideRef = dbRef.child("Ride/$currentCity/Rides/$currentRideUID")
        rideEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val ride = snapshot.getValue(Ride::class.java)!!
                    rideStatus(ride)
                    rideMonitor(ride)
                } else resetDriver()
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        rideRef?.addValueEventListener(rideEventListener!!)
    }

    private fun rideStatus(ride: Ride?) {
        if (ride?.status?.contains("APPROACHING") == true)
            ride.commuter?.values?.forEach { commuter ->
                if (commuter.status?.contains("ACTIVE") == true) {
                    if (!commuter.status.contains("DRIVING")) {
                        val distance = distanceBetween(commuter.current!!.toLatLng(),
                            ride.driver?.current!!.toLatLng())
                        if (distance < 15) setCommuterStatus(ride.uid!!, commuter.uid!!, "DRIVING")
                    }
                    if (!commuter.status.contains("COMPLETED") && commuter.status.contains("DRIVING")) {
                        val current = distanceBetween(commuter.current!!.toLatLng(),
                            ride.driver?.current!!.toLatLng())
                        val end = distanceBetween(commuter.end!!.toLatLng(),
                            commuter.current.toLatLng())
                        if (current > 15 && end < 15) setCommuterStatus(ride.uid!!, commuter.uid!!, "COMPLETED")
                    }
                }
            }
    }

    private fun setCommuterStatus(rideUID: String, commuterUID: String, status: String) {
        dbRef.child("Ride/$currentCity/Rides/$rideUID/commuter/$commuterUID/status")
            .runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    currentData.getValue(String::class.java)?.contains(status)?.let {
                        if (it) return Transaction.abort()
                    }
                    val states = currentData.getValue(String::class.java)?.split("|")?.toMutableList()
                    if (status == "COMPLETED") states?.clear()
                    states?.add(status)
                    currentData.value = states?.joinToString("|")
                    return Transaction.success(currentData)
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {}
            })
    }

    private fun rideMonitor(ride: Ride) {
        if (!ride.commuter.isNullOrEmpty()) {
            val commuters = ride.commuter.values.filter { it.status!!.contains("ACTIVE") }
            val completed = ride.commuter.values.filter { it.status!!.contains("COMPLETED_COMMUTER") }
            if (commuters.isNotEmpty() && commuters.all { it.status!!.contains("DRIVING") } && ride.status?.contains("DRIVING") == false) {
                dbRef.child("Ride/$currentCity/Rides/${ride.uid}/status")
                    .runTransaction(object : Transaction.Handler {
                        override fun doTransaction(currentData: MutableData): Transaction.Result {
                            currentData.getValue(String::class.java)?.contains("DRIVING")?.let {
                                if (!it) currentData.value = "DRIVING"
                            }
                            return Transaction.success(currentData)
                        }
                        override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {}
                    })
            } else if (completed.isNotEmpty()) {
                completed.forEach {
                    notifyRide("Commuter completed", "${it.name} has asked to complete their ride. Please confirm if this is correct.")
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!appInForeground) dbRef.child("Ride/$currentCity/Rides/${ride.uid}/commuter/${it.uid}/status").setValue("COMPLETED")
                    }, 10000)
                }
            } else if (ride.commuter.values.isNotEmpty() && commuters.isEmpty()) {
                notifyRide("Ride completed", "Your ride has been completed.")
                saveHistory(ride)
                val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                dbRef.child("Ride/$currentCity").updateChildren(hashMapOf<String, Any?>(
                    "/Driver/$uid/dateTime" to time,
                    "/Driver/$uid/busy" to false,
                    "/Rides/${ride.uid}" to null)).addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            removeRideListener()
                            currentRideUID = null
                            updateNotification()
                            driver?.apply {
                                dateTime = time
                                busy = false
                            }
                        }
                    }
            }
        } else resetDriver()
    }

    private fun resetDriver() {
        if (rideRef != null && rideEventListener != null) dbRef.child("Ride/$currentCity/Driver/$uid/busy").setValue(false)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    removeRideListener()
                    notifyRide("Ride canceled", "Your passenger/s have cancelled their ride with you.")
                    currentRideUID = null
                    updateNotification()
                }
            }
    }

    private fun saveHistory(ride: Ride) {
        val history = History(
            ride.reference,
            ride.solo,
            ride.commuter?.values?.joinToString("|") { it.startAddress ?: "" } ?: "",
            ride.commuter?.values?.joinToString("|") { it.endAddress ?: "" } ?: "",
            ride.passenger,
            ride.commuter?.values?.sumOf { it.price ?: 0 } ?: 0,
            ride.dateTime)
        dbRef.child("TodaGoDrive/Driver/$uid/History")
            .runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    if (currentData.child("${history.uid}").value != null) return Transaction.abort()
                    currentData.child("${history.uid}").value = history
                    return Transaction.success(currentData)
                }
                override fun onComplete(
                    error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?
                ) {
                    if (error != null) saveHistory(ride)
                }
            })
    }

    private fun fetchAPI(onComplete: (() -> Unit)? = null) {
        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) key = remoteConfig.getString("maps_api_key")
            onComplete?.invoke()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel("toda_go_location_channel", "Location Service",
            NotificationManager.IMPORTANCE_DEFAULT)
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val intent = getIntent(0)
        val notification = NotificationCompat.Builder(this, "toda_go_location_channel")
            .setContentTitle("TodaGo is monitoring your location")
            .setContentText(if (currentRideUID == null) "Waiting for rides . . ." else "Updating your location . . .")
            .setSmallIcon(R.drawable.ic_stat_todago)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.ic_stat_todago))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        if (!appInForeground) notification.setContentIntent(intent)
            .addAction(0, "View", intent)
        return notification.build()
    }

    private fun getIntent(code: Int): PendingIntent {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return pendingIntent
    }

    private fun updateNotification() {
        val notification = buildNotification()
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun notifyRide(title: String, message: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, "toda_go_location_channel")
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_stat_todago)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.ic_stat_todago))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
        val intent = getIntent(1)
        notification.setContentIntent(intent).addAction(0, "View", intent)
        manager.notify(1002, notification.build())
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        geocodeCall?.cancel()
        geocodeResponse = null
        geocodeCall = null
        activeEventListener?.let { activeRef?.removeEventListener(it) }
        activeEventListener = null
        activeRef = null
        removeRideListener()
        removeLocationUpdates()
        dbRef.child("Ride/$currentCity/Driver/$uid/active").setValue(false)
    }

    private fun removeRideListener() {
        rideEventListener?.let { rideRef?.removeEventListener(it) }
        rideEventListener = null
        rideRef = null
    }

    private fun removeLocationUpdates() {
        locationCallback?.let { fusedLocationClient?.removeLocationUpdates(it) }
        locationCallback = null
        fusedLocationClient = null
    }
}