package com.zinterr.todagodrive.ui.home

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.location.Location
import android.os.*
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.*
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Firebase
import com.google.firebase.database.*
import com.google.firebase.remoteconfig.*
import com.zinterr.todagodrive.R
import com.zinterr.todagodrive.TodaGoDrive
import com.zinterr.todagodrive.adapter.RideAdapter
import com.zinterr.todagodrive.databinding.DriverBinding
import com.zinterr.todagodrive.model.*
import com.zinterr.todagodrive.model.Global.getLocation
import com.zinterr.todagodrive.model.Global.setOnDebouncedClickListener
import com.zinterr.todagodrive.model.Global.toLatLng
import com.zinterr.todagodrive.model.Global.toLatLngString
import com.zinterr.todagodrive.model.Global.toLocalAddress
import com.zinterr.todagodrive.network.RetrofitClient
import com.zinterr.todagodrive.ui.popup.InfoDialog
import com.zinterr.todagodrive.ui.ride.RideActivity
import com.zinterr.todagodrive.util.*
import com.zinterr.todagodrive.viewmodel.*
import retrofit2.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

class Driver : Fragment(), RideClickListener {

    private lateinit var binding: DriverBinding
    private val currentViewViewModel: CurrentViewViewModel by activityViewModels()
    private val verifiedViewModel: VerifiedViewModel by activityViewModels()
    private val busyViewModel: BusyViewModel by viewModels()
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var availableRides: ArrayList<Ride>
    private lateinit var rideViewModel: RideViewModel
    private lateinit var session: SessionViewModel
    private lateinit var dbRef: DatabaseReference
    private var infoDialog: InfoDialog? = null
    private var runnable: Runnable? = null
    private var currentRide: Ride? = null
    private var isMonitoring = false
    private lateinit var user: User

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialize()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DriverBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAgreement()
        fetchAPI {
            loadData()
        }

        binding.loading.progress.setIndicatorColor(
            ContextCompat.getColor(requireContext(), R.color.blue_dark),
            ContextCompat.getColor(requireContext(), R.color.red))

        if (user.emailVerified == false || user.verified == false)
            verifiedViewModel.isVerified.observe(viewLifecycleOwner) { verified ->
                if (verified) binding.restrictUnverified.visibility = View.GONE
                else binding.restrictUnverified.visibility = View.VISIBLE
            }

        rideViewModel.ride.observe(viewLifecycleOwner) { ride ->
            observeRide(ride)
        }

        binding.rvRides.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(activity)
        }

        binding.btnStart.setOnDebouncedClickListener {
            startRides()
        }

        binding.btnStop.setOnDebouncedClickListener {
            stopRides()
        }
    }

    override fun onRideClick(rideUID: String) {
        getRide(rideUID)
    }

    override fun onStart() {
        super.onStart()
        if (session.driver.value?.active == true && session.driver.value?.busy == false) start()
        binding.btnStop.isEnabled = session.driver.value?.busy == false
    }

    override fun onResume() {
        super.onResume()
        currentViewViewModel.setCurrentView(requireView())
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRideMonitoring()
        if (infoDialog != null) if (infoDialog!!.isAdded) infoDialog!!.dismiss()
    }

    private fun initialize() {
        session = ViewModelProvider((requireActivity().application as TodaGoDrive),
            ViewModelProvider.AndroidViewModelFactory
                .getInstance((requireActivity().application as TodaGoDrive)))[SessionViewModel::class.java]
        rideViewModel = ViewModelProvider((requireActivity().application as TodaGoDrive),
            ViewModelProvider.AndroidViewModelFactory
                .getInstance((requireActivity().application as TodaGoDrive)))[RideViewModel::class.java]
        user = session.user.value!!
        dbRef = Firebase.database.reference
        remoteConfig = Firebase.remoteConfig
        availableRides = arrayListOf()
    }

    private fun fetchAPI(onComplete: (() -> Unit)? = null) {
        setupProgress("Loading data")
        remoteConfig.fetchAndActivate().addOnCompleteListener(requireActivity()) { task ->
            if (task.isSuccessful) {
                session.setKey(remoteConfig.getString("maps_api_key"))
                session.setFee(remoteConfig.getDouble("service_fee"))
            }
            onComplete?.invoke()
        }
    }

    private fun loadData() {
        if (user.verified!!) {
            if (session.matrix.value == null) getMatrix()
            checkActive {
                checkActiveRide()
            }
        } else endProgress()
    }

    private fun getMatrix() {
        dbRef.child("Matrix/${user.assoc}").addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val matrix = snapshot.getValue(Matrix::class.java)
                        session.setMatrix(matrix)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun startRides() {
        setupProgress("Starting your day")
        if (session.driver.value == null) getDriver { start() } else start()
    }

    private fun start() {
        startRideMonitoring()
        session.driver.value?.let { driver ->
            driver.active = true
            session.setDriver(driver)
        }
        LocationService.startService(requireContext(), session.city.value)
        endProgress()
        toggleButtons(true)
        busyViewModel.setBusy(true)
    }

    private fun startRideMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        val interval = 5000L
        runnable = object : Runnable {
            override fun run() {
                if (!isMonitoring) return
                val ref = dbRef.child("Ride/${session.city.value}/Rides")
                ref.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        availableRides.clear()
                        if (snapshot.exists()) {
                            for (item in snapshot.children) {
                                item.getValue(Ride::class.java)?.let {
                                    if (it.driver == null && isRideAvailable(it))
                                        availableRides.add(it)
                                }
                            }
                        }
                        binding.rvRides.adapter = RideAdapter(availableRides, this@Driver)
                        if (availableRides.isNotEmpty()) binding.waitingRides.visibility = View.GONE
                        else binding.waitingRides.visibility = View.VISIBLE
                        if (isMonitoring) handler.postDelayed(runnable!!, interval)
                    }
                    override fun onCancelled(error: DatabaseError) {
                        if (isMonitoring) handler.postDelayed(runnable!!, interval)
                    }
                })
            }
        }
        handler.post(runnable!!)
    }

    private fun stopRideMonitoring() {
        isMonitoring = false
        runnable?.let { handler.removeCallbacksAndMessages(it) }
        runnable = null
    }

    private fun isRideAvailable(ride: Ride): Boolean {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        if (session.driver.value != null) {
            if (session.driver.value?.dateTime != null ) {
                val driverDateTime = LocalDateTime.parse(session.driver.value?.dateTime, formatter)
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
        val locDiff = distanceBetween(start!!, session.current.value!!)
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
        distanceBetween(it.start!!.toLatLng(), session.current.value!!)
    }

    private fun distanceBetween(p1: LatLng, p2: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(p1.latitude, p1.longitude,
            p2.latitude, p2.longitude, results)
        return results[0]
    }

    private fun stopRides() {
        setupProgress("Taking a break")
        dbRef.child("Ride/${session.city.value}/Driver/${user.uid}/active").setValue(false)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    session.driver.value?.let { driver ->
                        driver.apply {
                            active = false
                            busy = false
                        }
                        session.setDriver(driver)
                    }
                    LocationService.stopService()
                    toggleButtons(false)
                    binding.btnStop.isEnabled = true
                    endProgress()
                    busyViewModel.setBusy(false)
                    availableRides.clear()
                } else {
                    endProgress()
                    snackBar(view, "Error: ${task.exception?.message}")
                }
            }
    }

    private fun toggleButtons(isActive: Boolean) {
        binding.btnStart.visibility = if (isActive) View.GONE else View.VISIBLE
        binding.terms.visibility = if (isActive) View.GONE else View.VISIBLE
        binding.activeLayout.visibility = if (isActive) View.VISIBLE else View.GONE
        binding.btnStop.isEnabled = session.driver.value?.busy == false
    }

    private fun observeRide(ride: Ride?) {
        if (ride != null && currentRide == null) {
            currentRide = ride
            toggleButtons(true)
            binding.btnStop.isEnabled = false
            val intent = Intent(requireContext(), RideActivity::class.java)
            startActivity(intent)
        } else if (currentRide != null) {
            currentRide = null
            LocationService.startService(requireContext(), session.city.value)
            toggleButtons(true)
        }
    }

    private fun checkActive(onComplete: (() -> Unit)? = null) {
        getLocation(requireContext()) { latLng ->
            if (latLng != null) {
                session.setCurrent(latLng)
                getCity(user.location!!.toLatLng()) { success ->
                    if (success) {
                        getDriver { gotDriver ->
                            if (gotDriver) {
                                if (session.driver.value?.active == true || session.driver.value?.busy == true) {
                                    if (session.driver.value?.busy == true) {
                                        busyViewModel.setBusy(true)
                                        binding.btnStop.isEnabled = false
                                        onComplete?.invoke()
                                    } else start()
                                    toggleButtons(true)
                                } else endProgress()
                            } else loadData()
                        }
                    } else loadData()
                }
            } else loadData()
        }
    }

    private fun getRide(rideUID: String) {
        dbRef.child("Ride/${session.city.value}/Rides/$rideUID")
            .runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    if (!availableRides.any { it.uid == rideUID }) return Transaction.abort()
                    currentData.child("driver").getValue(RideDriver::class.java)?.let {
                        return Transaction.abort()
                    }
                    val solo = currentData.child("solo").getValue(Boolean::class.java)
                    val passenger = currentData.child("passenger").getValue(Int::class.java)
                    val weight = currentData.child("weight").getValue(Int::class.java)
                    val states = currentData.child("status").getValue(String::class.java)
                        ?.split("|")?.toMutableList()
                    states?.remove("WAITING_DRIVER")
                    solo?.let { solo ->
                        passenger?.let { passenger ->
                            weight?.let { weight ->
                                if (passenger == 3 || weight == 5 || solo)
                                    states?.remove("WAITING_PASSENGER")
                            }
                        }
                    }
                    currentData.child("commuter").children.forEach { value ->
                        val commuter = value.getValue(Commuter::class.java)
                        if (commuter != null) {
                            val cost = getRideCost(commuter.discount != null, solo!!,
                                passenger!!, commuter.distance!!)
                            val rideCost = cost.first - cost.second
                            val serviceFee = getServiceFee(commuter, cost.first)
                            val total = (rideCost + (if (isSpecial(solo, passenger, commuter.distance)) 0F else (cost.first * (commuter.passenger!! - 1))) + serviceFee).roundToInt()
                            currentData.child("commuter/${commuter.uid}/price").value = total
                        }
                    }
                    currentData.child("driver").value = RideDriver(
                        session.user.value?.uid,
                        session.user.value?.name,
                        session.user.value?.plate,
                        "%.2f".format(session.user.value?.rating?.div(session.user.value?.rates!!)),
                        session.current.value?.toLatLngString(),
                        session.user.value?.phone,
                        session.user.value?.profile,
                        session.matrix.value)
                    currentData.child("status").value = states?.joinToString("|")
                    return Transaction.success(currentData)
                }
                override fun onComplete(error: DatabaseError?,
                    committed: Boolean, currentData: DataSnapshot?
                ) {
                    if (error != null) Log.d("ERROR", error.message)
                    else if (committed) {
                        stopRideMonitoring()
                        dbRef.child("Ride/${session.city.value}/Driver/${session.user.value?.uid}/busy").setValue(true)
                        session.driver.value?.let { driver ->
                            driver.busy = true
                            session.setDriver(driver)
                        }
                        rideViewModel.setRidePath(session.city.value!!, rideUID)
                        LocationService.startService(requireContext(), session.city.value, rideUID)
                    } else {
                        infoDialog = InfoDialog("Sorry. The ride has already been canceled or taken by another driver.")
                        infoDialog!!.show(childFragmentManager, "InfoDialog")
                        availableRides.removeIf { it.uid == rideUID }
                        binding.rvRides.adapter = RideAdapter(availableRides, this@Driver)
                        if (availableRides.isNotEmpty()) binding.waitingRides.visibility = View.GONE
                        else binding.waitingRides.visibility = View.VISIBLE
                    }
                    endProgress()
                }
            })
    }

    private fun getServiceFee(commuter: Commuter, cost: Float): Int = ((cost * commuter.passenger!!) * (session.fee.value!!/100))
        .roundToInt().coerceAtLeast(1)

    private fun getRideCost(isDiscount: Boolean, solo: Boolean, passenger: Int, distance: Int): Pair<Float, Float> {
        val perKM = if (isSpecial(solo, passenger, distance)) session.matrix.value?.specialPerKM else session.matrix.value?.perKm
        val discount = if (isDiscount) session.matrix.value?.discount else 0
        val discounted = ((distance/1000F) * perKM!!) * discount!!
        return Pair(((distance/1000F) * perKM).coerceAtLeast(if (isSpecial(solo, passenger, distance)) 100F else 40F),
            discounted)
    }

    private fun isSpecial(solo: Boolean, passenger: Int, distance: Int): Boolean = solo && passenger <= 2 && distance / 1000F > 1.5F

    private fun getDriver(onComplete: (Boolean) -> Unit) {
        dbRef.child("Ride/${session.city.value}/Driver/${user.uid}")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val driver = snapshot.getValue(RideDriver::class.java)
                        driver?.let {
                            session.setDriver(it)
                            onComplete(true)
                        }
                    } else setDriver { set -> onComplete(set) }
                }
                override fun onCancelled(error: DatabaseError) {
                    onComplete(false)
                    snackBar(view, "Error: ${error.message}")
                }
            })
    }

    private fun setDriver(onComplete: (Boolean) -> Unit) {
        val driver = RideDriver(current = session.current.value?.toLatLngString(), busy = false, active = false, assoc = user.assoc)
        dbRef.child("Ride/${session.city.value}/Driver/${user.uid}").setValue(driver)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    session.setDriver(driver)
                    onComplete(true)
                } else onComplete(false)
            }
    }

    // NEED A CHECK FOR COMPLETED RIDE IN CASE BOTH THE COMMUTER AND DRIVER GOES OFFLINE DURING THE RIDE
    private fun checkActiveRide() {
        dbRef.child("Ride/${session.city.value}/Rides").addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (item in snapshot.children) {
                            val ride = item.getValue(Ride::class.java)!!
                            if (ride.uid == null) dbRef.child("Ride/${session.city.value}/Rides/${item.key}").setValue(null)
                            else if (ride.driver != null && ride.driver.uid == user.uid) {
                                session.driver.value?.let { driver ->
                                    driver.apply {
                                        active = true
                                        busy = true
                                    }
                                    session.setDriver(driver)
                                }
                                rideViewModel.setRidePath(session.city.value!!, ride.uid)
                                LocationService.startService(requireContext(),
                                    session.city.value!!, ride.uid)
                            }
                        }
                    } else {
                        infoDialog = InfoDialog("The ride you were in has been canceled.")
                        infoDialog!!.show(childFragmentManager, "InfoDialog")
                        dbRef.child("Ride/${session.city.value}/Driver/${user.uid}/busy").setValue(false)
                        session.driver.value?.let { driver ->
                            driver.busy = false
                            session.setDriver(driver)
                        }
                        toggleButtons(false)
                    }
                    endProgress()
                }
                override fun onCancelled(error: DatabaseError) {
                    endProgress()
                    snackBar(view, "Error: ${error.message}")
                }
            })
    }

    private fun getCity(latLng: LatLng, onComplete: (Boolean) -> Unit) {
        RetrofitClient.geocodeService.reverseGeocode(latLng.toLatLngString(), session.key.value!!)
            .enqueue(object : Callback<GeocodeResponse> {
                override fun onResponse(call: Call<GeocodeResponse?>, response: Response<GeocodeResponse?>) {
                    if (response.isSuccessful) {
                        session.setCity(response.body()?.toLocalAddress()!!.city)
                        onComplete(true)
                    } else onComplete(false)
                }
                override fun onFailure(call: Call<GeocodeResponse?>, t: Throwable) {
                    onComplete(false)
                }
            })
    }

    private fun setupAgreement() {
        val text = "By proceeding, I agree to TodaGo's Terms and Privacy Policies"
        val ss = SpannableString(text)
        val startT = text.indexOf("Terms")
        val endT = startT + "Terms".length
        val startP = text.indexOf("Privacy")
        val endP = text.length
        ss.setSpan(ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.blue_dark)),
            startT, endT, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ss.setSpan(StyleSpan(Typeface.BOLD_ITALIC), startT, endT, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ss.setSpan(ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.blue_dark)),
            startP, endP, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ss.setSpan(StyleSpan(Typeface.BOLD_ITALIC), startP, endP, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.terms.text = ss
        binding.terms.highlightColor = Color.TRANSPARENT
    }

    private fun setupProgress(message: String) {
        binding.loading.message.apply {
            visibility = if (message.isEmpty()) View.GONE
            else View.VISIBLE
            text = message
        }
        binding.loading.container.visibility = View.VISIBLE
        Home().addCallbackFalse()
    }

    private fun endProgress() {
        binding.loading.container.visibility = View.GONE
        Home().addCallbackTrue()
    }
}