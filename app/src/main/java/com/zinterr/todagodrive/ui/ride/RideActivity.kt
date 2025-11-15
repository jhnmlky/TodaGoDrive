package com.zinterr.todagodrive.ui.ride

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.*
import android.location.Location
import android.os.*
import android.view.*
import android.widget.*
import androidx.activity.*
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.*
import androidx.core.net.toUri
import androidx.core.view.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.firebase.Firebase
import com.google.firebase.database.*
import com.google.firebase.remoteconfig.*
import retrofit2.*
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.maps.android.PolyUtil
import com.squareup.picasso.Picasso
import com.zinterr.todagodrive.databinding.ActivityRideBinding
import com.zinterr.todagodrive.ui.popup.*
import com.zinterr.todagodrive.R
import com.zinterr.todagodrive.adapter.PassengerAdapter
import com.zinterr.todagodrive.model.Global.setOnDebouncedClickListener
import com.zinterr.todagodrive.model.Global.toLatLng
import com.zinterr.todagodrive.model.*
import com.zinterr.todagodrive.network.RetrofitClient
import com.zinterr.todagodrive.util.*
import com.zinterr.todagodrive.viewmodel.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@SuppressLint("SetTextI18n")
class RideActivity : AppCompatActivity(), OnMapReadyCallback, PassengerCallClickListener {

    private lateinit var binding: ActivityRideBinding
    private var locationEventListener: ValueEventListener? = null
    private val rideViewModel: RideViewModel by viewModels()
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private lateinit var polylines: ArrayList<Polyline>
    private var locationRef: DatabaseReference? = null
    private var confirmDialog: ConfirmDialog? = null
    private var bounds: LatLngBounds.Builder? = null
    private lateinit var markers: ArrayList<Marker>
    private lateinit var dbRef: DatabaseReference
    private var infoDialog: InfoDialog? = null
    private lateinit var account: Account
    private var currentRide: Ride? = null
    private var location: LatLng? = null
    private var map: GoogleMap? = null
    private var isView: Boolean? = null
    private lateinit var user: User
    private var canceled = false
    private val callbackFalse = object : OnBackPressedCallback(true) { override fun handleOnBackPressed() {} }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityRideBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.rideActivity) { _, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            binding.bottomSheet.setPadding(0, 0, 0, 40 + navBars.bottom)
            insets
        }
        initialize()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_ride) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fetchAPI {
            if (isView == true) setupView()
            else rideViewModel.ride.observe(this) { ride ->
                observeRide(ride)
            }
        }

        binding.loading.progress.setIndicatorColor(
            "#1561FF".toColorInt(),
            "#FFBF0D3E".toColorInt())

        binding.rvPassenger.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@RideActivity)
        }

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnCamera.setOnDebouncedClickListener {
            if (isView != true) animateZoom()
        }

        binding.btnStart.setOnDebouncedClickListener {
            startRide()
        }

        binding.btnComplete.setOnDebouncedClickListener {
            completeRide()
        }

        binding.btnCancel.setOnDebouncedClickListener {
            cancelRide()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map?.setMapStyle(MapStyleOptions.loadRawResourceStyle(
            this, R.raw.map_style))
        map?.setMinZoomPreference(10f)
        map?.setMaxZoomPreference(40f)
        if (isView == true && location != null) {
            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(location!!, 16f))
            displayDriver(location!!)
        }
        setupBottomSheet()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (confirmDialog != null) if (confirmDialog!!.isAdded) confirmDialog!!.dismiss()
        if (infoDialog != null) if (infoDialog!!.isAdded) infoDialog!!.dismiss()
        locationEventListener?.let { locationRef?.removeEventListener(it) }
        locationEventListener = null
        locationRef = null
    }

    override fun onCallClick(phone: String) {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = "tel:$phone".toUri()
        }
        startActivity(intent)
    }

    override fun onChatClick(uid: String) {
        snackBar(binding.root, "WIP: Live chat feature")
    }

    private fun initialize() {
        remoteConfig = Firebase.remoteConfig
        dbRef = Firebase.database.reference
        account = Global.account!!
        user = Global.user!!
        markers = arrayListOf()
        polylines = arrayListOf()
        isView = intent.getBooleanExtra("VIEW", false)
        if (isView == true) location = Global.activeUser?.current?.toLatLng()
    }

    private fun observeRide(ride: Ride?) {
        if (ride != null) {
            currentRide = ride
            setupPassenger(ride)
            updateMap(ride)
            rideStatus(ride)
            rideMonitor(ride)
        } else if (currentRide != null) {
            if (!canceled) {
                infoDialog = InfoDialog("Sorry. Your passenger/s have cancelled their ride " +
                        "with you.${if (currentRide?.status?.contains("APPROACHING") == true)
                            " Don't worry, we've penalized the passenger's Trust Rating for this act." else ""}") {
                    rideViewModel.resetRide()
                    finish()
                }
                infoDialog!!.show(supportFragmentManager, "InfoDialog")
                dbRef.child("Ride/${Global.city}/Driver/${user.uid}/busy").setValue(false)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) Global.driver?.busy = false
                    }
            } else {
                infoDialog = InfoDialog("You have delivered all your passenger. Thank you.") {
                    finish()
                }
                infoDialog!!.show(supportFragmentManager, "InfoDialog")
            }
        }
    }

    // NAVIGATION SDK OPTION
    private fun startRide() {
        setupProgress("Processing")
        currentRide?.let { ride ->
            dbRef.child("Ride/${Global.city}/Rides/${ride.uid}/status").setValue("APPROACHING")
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        binding.btnStart.visibility = View.GONE
                        binding.btnCancel.text = "Cancel Ride (Penalty)"
                        setPadding(binding.rideActivity.height, binding.bottomSheet.top)
                    }
                    endProgress()
                }
        }
    }

    private fun completeRide() {
        currentRide?.let { ride ->
            confirmDialog = ConfirmDialog("Finish ride",
                "Have you delivered your passenger/s to their destination/s?",
                "Finish", "Cancel") { confirm ->
                if (confirm) complete(ride)
            }
            confirmDialog!!.show(supportFragmentManager, "ConfirmDialog")
        }
    }

    private fun complete(ride: Ride) {
        setupProgress("Processing")
        val updates = hashMapOf<String, Any>()
        ride.commuter!!.values.forEach {
            updates["/${it.uid}/status"] = "COMPLETED"
        }
        dbRef.child("Ride/${Global.city}/Rides/${ride.uid}/commuter").updateChildren(updates)
            .addOnCompleteListener { task ->
                if (task.exception != null) snackBar(binding.root, "Error: ${task.exception!!.message}")
                endProgress()
            }
    }

    private fun cancelRide() {
        currentRide?.let { ride ->
            confirmDialog = ConfirmDialog("Cancel ride",
                "Are you sure you want to cancel this ride?" +
                        if (ride.status?.contains("APPROACHING") == true || ride.status?.contains("DRIVING") == true)
                            " Since this action may cause inconvenience to your passenger/s, you may receive a " +
                                "penalty on your Trust Rating." else "",
                "Proceed", "No") { confirm ->
                if (confirm) cancel(ride, ride.status?.contains("APPROACHING") == true || ride.status?.contains("DRIVING") == true)
            }
            confirmDialog!!.show(supportFragmentManager, "ConfirmDialog")
        }
    }

    private fun cancel(ride: Ride, penalty: Boolean) {
        canceled = true
        setupProgress("Processing")
        val updates = hashMapOf<String, Any?>(
            "/Rides/${ride.uid}/status" to "WAITING_DRIVER${if (ride.status?.contains("WAITING_PASSENGER") == true) "|WAITING_PASSENGER" else ""}",
            "/Rides/${ride.uid}/driver" to null, "/Driver/${user.uid}/busy" to false)
        ride.commuter?.values?.forEach {
            updates["/Rides/${ride.uid}/commuter/${it.uid}/status"] = if (it.status?.contains("COMMUTER") == true)
                "COMPLETED_COMMUTER" else if (it.status?.contains("COMPLETED") == true) "COMPLETED" else "ACTIVE"
        }
        dbRef.child("Ride/${Global.city}").updateChildren(updates)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Global.driver?.busy = false
                    rideViewModel.resetRide()
                    if (penalty) {
                        dbRef.child("TodaGoDrive/Driver/${user.uid}").updateChildren(
                            mapOf("/rating" to user.rating?.plus(2F),
                                "/rates" to user.rates?.plus(1)))
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    snackBar(binding.root, "You have canceled the ride")
                                    Global.user?.apply {
                                        rating = rating?.plus(2F)
                                        rates = rates?.plus(1)
                                    }
                                    finish()
                                }
                            }
                    } else {
                        snackBar(binding.root, "You have canceled the ride")
                        finish()
                    }
                } else cancel(ride, penalty)
            }
    }

    private fun setupPassenger(ride: Ride) {
        if (ride.commuter != null) {
            setPadding(binding.rideActivity.height, binding.bottomSheet.top)
            binding.rideStatus.apply {
                visibility = if ((ride.solo == false && ride.passenger!! < 3) || (ride.solo == true && ride.status?.isEmpty() == true)) View.VISIBLE else View.GONE
                if (ride.solo == true && ride.status?.isEmpty() == true) text = "You can now start the ride"
            }
            binding.rvPassenger.adapter = PassengerAdapter(ArrayList(ride.commuter.values.filter { it.status!!.contains("ACTIVE") }), this)
            if (ride.passenger!! > 1) binding.passengerCount.text = "${ride.passenger}/3"
            else binding.passengerTitle.text = "Passenger"
        }
    }

    private fun updateMap(ride: Ride) {
        if (ride.driver != null) {
            val origin = ride.driver.current
            collectWaypoints(origin!!, ride) { waypoints, end ->
                end?.let { displayRoute(origin, it, waypoints?.joinToString("|")) }
            }
        }
    }

    private fun rideStatus(ride: Ride?) {
        if (ride?.driver != null && (ride.status?.contains("APPROACHING") == true || ride.status?.contains("DRIVING") == true)) {
            ride.commuter?.values?.forEach { commuter ->
                if (commuter.status?.contains("ACTIVE") == true) {
                    if (!commuter.status.contains("DRIVING")) {
                        val distance = distanceBetween(commuter.current!!.toLatLng(),
                            ride.driver.current!!.toLatLng())
                        if (distance < 15) setCommuterStatus(ride.uid!!, commuter.uid!!, "DRIVING")
                    }
                    if (!commuter.status.contains("COMPLETED") && commuter.status.contains("DRIVING")) {
                        val current = distanceBetween(commuter.current!!.toLatLng(),
                            ride.driver.current!!.toLatLng())
                        val end = distanceBetween(commuter.end!!.toLatLng(),
                            commuter.current.toLatLng())
                        if (current > 15 && end < 15) setCommuterStatus(ride.uid!!, commuter.uid!!, "COMPLETED")
                    }
                }
            }
        }
    }

    private fun setCommuterStatus(rideUID: String, commuterUID: String, status: String) {
        dbRef.child("Ride/${Global.city}/Rides/$rideUID/commuter/$commuterUID/status")
            .runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    currentData.getValue(String::class.java)?.contains(status)?.let {
                        if (it) return Transaction.abort()
                    }
                    val states = currentData.getValue(String::class.java)?.split("|")?.toMutableList()
                    if (status == "COMPLETED") states?.clear()
                    states?.add(status)
                    currentData.value = states?.joinToString("|") ?: ""
                    return Transaction.success(currentData)
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {}
            })
    }

    private fun distanceBetween(p1: LatLng, p2: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(p1.latitude, p1.longitude,
            p2.latitude, p2.longitude, results)
        return results[0]
    }

    private fun rideMonitor(ride: Ride) {
        if (!ride.commuter.isNullOrEmpty()) {
            if (ride.status?.contains("DRIVING") == true) {
                binding.btnComplete.visibility = View.VISIBLE
                binding.btnStart.visibility = View.GONE
            }
            val commuters = ride.commuter.values.filter { it.status!!.contains("ACTIVE") }
            val completed = ride.commuter.values.filter { it.status!!.contains("COMPLETED_COMMUTER") }
            if (commuters.isNotEmpty() && commuters.all { it.status!!.contains("DRIVING") } && ride.status?.contains("DRIVING") == false) {
                dbRef.child("Ride/${Global.city}/Rides/${ride.uid}/status")
                    .runTransaction(object : Transaction.Handler {
                        override fun doTransaction(currentData: MutableData): Transaction.Result {
                            currentData.getValue(String::class.java)?.contains("DRIVING")?.let {
                                if (!it) currentData.value = "DRIVING"
                            }
                            return Transaction.success(currentData)
                        }
                        override fun onComplete(
                            error: DatabaseError?,
                            committed: Boolean, currentData: DataSnapshot?
                        ) {
                            if (committed) binding.btnComplete.visibility = View.VISIBLE
                        }
                    })
            } else if (completed.isNotEmpty()) {
                completed.forEach {
                    val state = it.status?.split("|")?.toMutableList()
                    state?.remove("COMPLETED_COMMUTER")
                    confirmDialog = ConfirmDialog("Commuter completed",
                        "${it.name} has asked to complete their ride with you. Please confirm if this is correct.",
                        "Yes", "No", true) { confirmed ->
                        dbRef.child("Ride/${Global.city}/Rides/${ride.uid}/commuter/${it.uid}/status")
                            .setValue(if (confirmed) "COMPLETED" else state?.joinToString("|") ?: "")
                    }
                    confirmDialog!!.show(supportFragmentManager, "ConfirmDialog")
                }
            } else if (ride.commuter.values.isNotEmpty() && commuters.isEmpty()) {
                canceled = true
                saveHistory(ride)
                val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                dbRef.child("Ride/${Global.city}").updateChildren(hashMapOf<String, Any?>(
                    "/Driver/${user.uid}/dateTime" to time,
                    "/Driver/${user.uid}/busy" to false,
                    "/Rides/${ride.uid}" to null)).addOnCompleteListener {
                        dbRef.child("TodaGoDrive/Driver/${user.uid}").updateChildren(
                            mapOf("/rating" to user.rating?.plus(5F),
                                "/rates" to user.rates?.plus(1)))
                        rideViewModel.resetRide()
                        Global.driver?.apply {
                            dateTime = time
                            busy = false
                        }
                    }
            }
        }
    }

    private fun collectWaypoints(origin: String, ride: Ride, onComplete: (List<String>?, String?) -> Unit) {
        val commuters = ride.commuter?.values?.filter { it.status?.contains("ACTIVE") == true }
            ?.toList()
        if (commuters.isNullOrEmpty()) {
            onComplete(null, null)
            return
        }
        val pickups = commuters.map { if (it.status!!.contains("DRIVING")) it.current!! else it.start!! }
        val drops = commuters.map { it.end!! }
        val destinations = pickups.joinToString("|")
        RetrofitClient.distanceMatrixService.getDistanceMatrix(origin, destinations, Global.key!!)
            .enqueue(object : Callback<DistanceMatrixResponse> {
                override fun onResponse(
                    call: Call<DistanceMatrixResponse>,
                    response: Response<DistanceMatrixResponse>
                ) {
                    if (!response.isSuccessful) {
                        onComplete(null, null)
                        return
                    }
                    val matrix = response.body()
                    val distances = matrix?.rows?.firstOrNull()?.elements ?: return
                    val pickupWithDistance = pickups.zip(distances).map { (pickup, elem) ->
                        pickup to (elem.distance?.value ?: Int.MAX_VALUE)
                    }
                    val sortedPickups = pickupWithDistance.sortedBy { it.second }
                    val sortedDrops = drops.sortedByDescending {
                        commuters.find { c -> c.end == it }?.distance ?: 0
                    }
                    val waypoints = mutableListOf<String>()
                    waypoints.addAll(sortedPickups.map { it.first })
                    waypoints.addAll(sortedDrops.dropLast(1))
                    val destination = sortedDrops.last()
                    onComplete(waypoints, destination)
                }
                override fun onFailure(call: Call<DistanceMatrixResponse>, t: Throwable) {}
            })
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
        dbRef.child("TodaGoDrive/Driver/${account.uid}/History")
            .runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    if (currentData.child("${history.uid}").value != null) return Transaction.abort()
                    currentData.child("${history.uid}").value = history
                    return Transaction.success(currentData)
                }
                override fun onComplete(error: DatabaseError?,
                    committed: Boolean, currentData: DataSnapshot?
                ) {
                    if (error != null) saveHistory(ride)
                }
            })
    }

    private fun setupBottomSheet() {
        if (isView != true) {
            setPadding(binding.rideActivity.height, binding.bottomSheet.top)
            val behavior = BottomSheetBehavior.from(binding.bottomSheet)
            behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    setPadding(binding.rideActivity.height, bottomSheet.top)
                }
                override fun onStateChanged(bottomSheet: View, state: Int) {
                    if (state == BottomSheetBehavior.STATE_EXPANDED ||
                        state == BottomSheetBehavior.STATE_COLLAPSED) {
                        animateZoom()
                    }
                }
            })
        } else {
            binding.btnCamera.setOnDebouncedClickListener {
                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(location!!, 16f))
            }
            binding.bottomSheet.visibility = View.GONE
        }
    }

    private fun setPadding(height: Int, top: Int) {
        map?.setPadding(0, 0, 0, (height * 0.05).toInt())
        binding.mapRide.setPadding(0, 0, 0,
            ((height - top) - (height * 0.05)).toInt())
        binding.bottomSheet.requestLayout()
    }

    private fun fetchAPI(onComplete: (() -> Unit)? = null) {
        setupProgress("Loading data")
        remoteConfig.fetchAndActivate().addOnCompleteListener(this) { task ->
            if (task.isSuccessful) Global.key = remoteConfig.getString("maps_api_key")
            endProgress()
            onComplete?.invoke()
        }
    }

    private fun animateZoom() {
        val bounds = bounds!!.build()
        map?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 200))
    }

    private fun displayRoute(origin: String, destination: String, waypoints: String? = null) {
        RetrofitClient.directionsService.getDirections(origin, destination, Global.key!!, "$waypoints")
            .enqueue(object : Callback<DirectionsResponse> {
                override fun onResponse(call: Call<DirectionsResponse>, response: Response<DirectionsResponse>) {
                    if (response.isSuccessful) {
                        markers.forEach { it.remove() }
                        markers.clear()
                        polylines.forEach { it.remove() }
                        polylines.clear()
                        var duration = 0L
                        var distance = 0L
                        val route = response.body()?.routes?.firstOrNull()
                        route?.let {
                            bounds = LatLngBounds.Builder().include(it.bounds.northeast.toLatLng())
                                .include(it.bounds.southwest.toLatLng())
                            animateZoom()
                        }
                        route?.legs?.forEachIndexed { index, leg ->
                            duration += leg.duration.value
                            distance += leg.distance.value
                            leg.steps.forEach { step ->
                                val points = PolyUtil.decode(step.polyline.points)
                                drawPolyline(points)
                            }
                            val marker = if (index == 0) map?.addMarker(MarkerOptions().position(leg.start.toLatLng()).icon(BitmapDescriptorFactory.fromBitmap(getMarker(true))))!!
                            else map?.addMarker(MarkerOptions().position(leg.start.toLatLng()).icon(BitmapDescriptorFactory.fromBitmap(getMarker(false))))!!
                            markers.add(marker)
                            if (index == route.legs.lastIndex) {
                                val mark = map?.addMarker(MarkerOptions().position(leg.end.toLatLng()).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)))!!
                                markers.add(mark)
                            }
                        }
                        setupRide(duration, distance)
                    }
                }
                override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {}
            })
    }

    private fun setupRide(duration: Long, distance: Long) {
        binding.rideDuration.apply {
            if (text.isNullOrEmpty()) text = formatDuration(duration)
        }
        binding.rideDistance.apply {
            if (text.isNullOrEmpty()) text = "${"%.2f".format(distance / 1000F)} km"
        }
    }

    private fun formatDuration(seconds: Long): String {
        val hour = seconds / 3600
        val minute = (seconds % 3600) / 60
        val secs = seconds % 60
        return when {
            hour > 0 -> "%d hr %02d min %02d sec".format(hour, minute, secs)
            minute > 0 -> "%d min %02d sec".format(minute, secs)
            else -> "%d sec".format(secs)
        }
    }

    private fun drawPolyline(points: List<LatLng>) {
        val polyline = map?.addPolyline(PolylineOptions()
            .color("#1561FF".toColorInt())
            .addAll(points)
            .width(20f)
            .geodesic(true)
            .endCap(RoundCap())
            .startCap(RoundCap()))
        polylines.add(polyline!!)
    }

    @SuppressLint("InflateParams")
    private fun getMarker(driver: Boolean): Bitmap {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.marker_pin, null)

        val image = view.findViewById<ImageView>(R.id.icon)
        if (driver) {
            if (user.profile != null) Picasso.get()
                .load(user.profile)
                .placeholder(R.drawable.tricycle)
                .into(image)
            else image.setImageResource(R.drawable.tricycle)
        } else image.setImageResource(R.drawable.commuter)

        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)

        val bitmap = createBitmap(view.measuredWidth, view.measuredHeight)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    private fun setupView() {
        setupProgress("Loading data")
        location = Global.activeUser?.current?.toLatLng()
        binding.headerTitle.visibility = View.VISIBLE
        val layoutParams = binding.camera.layoutParams as CoordinatorLayout.LayoutParams
        layoutParams.anchorId = R.id.map_ride
        layoutParams.anchorGravity = Gravity.BOTTOM or Gravity.END
        binding.camera.layoutParams = layoutParams
        binding.camera.translationY = 0f
        if (Global.activeUser?.busy == true) {
            locationRef = dbRef.child("Ride/${Global.city}/Rides/")
            locationEventListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (item in snapshot.children) {
                            val ride = item.getValue(Ride::class.java)
                            if (ride != null && ride.driver?.uid == Global.activeUser?.uid) {
                                location = ride.driver?.current?.toLatLng()
                                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(location!!, 16f))
                                displayDriver(location!!)
                            }
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(binding.root, "Error: ${error.message}")
                }
            }
            locationRef?.orderByChild("driver")?.addValueEventListener(locationEventListener!!)
        } else {
            locationRef = dbRef.child("Ride/${Global.city}/Driver/${Global.activeUser?.uid}")
            locationEventListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val driver = snapshot.getValue(RideDriver::class.java)
                        location = driver?.current?.toLatLng()
                        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(location!!, 16f))
                        displayDriver(location!!)
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(binding.root, "Error: ${error.message}")
                }
            }
            locationRef?.addValueEventListener(locationEventListener!!)
        }
        endProgress()
    }

    private fun displayDriver(location: LatLng) {
        markers.forEach { it.remove() }
        markers.clear()
        val marker = map?.addMarker(MarkerOptions().position(location).icon(
            BitmapDescriptorFactory.fromBitmap(getMarker(true))))!!
        markers.add(marker)
    }

    private fun setupProgress(message: String) {
        binding.loading.message.apply {
            visibility = if (message.isEmpty()) View.GONE
            else View.VISIBLE
            text = message
        }
        binding.loading.container.visibility = View.VISIBLE
        onBackPressedDispatcher.addCallback(this, callbackFalse)
    }

    private fun endProgress() {
        binding.loading.container.visibility = View.GONE
        callbackFalse.remove()
    }
}