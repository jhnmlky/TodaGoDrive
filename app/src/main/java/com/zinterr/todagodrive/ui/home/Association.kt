package com.zinterr.todagodrive.ui.home

import android.annotation.SuppressLint
import android.content.Intent
import android.os.*
import androidx.fragment.app.Fragment
import android.view.*
import android.widget.SearchView
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.tabs.TabLayout
import com.google.firebase.Firebase
import com.google.firebase.auth.*
import com.google.firebase.database.*
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import com.zinterr.todagodrive.R
import com.zinterr.todagodrive.adapter.*
import com.zinterr.todagodrive.databinding.AssociationBinding
import com.zinterr.todagodrive.model.*
import com.zinterr.todagodrive.model.Global.hideKeyboard
import com.zinterr.todagodrive.model.Global.toLatLng
import com.zinterr.todagodrive.model.Global.toLatLngString
import com.zinterr.todagodrive.model.Global.toLocalAddress
import com.zinterr.todagodrive.network.RetrofitClient
import com.zinterr.todagodrive.ui.ride.RideActivity
import com.zinterr.todagodrive.util.*
import com.zinterr.todagodrive.viewmodel.*
import retrofit2.*

@SuppressLint("SetTextI18n")
class Association : Fragment(), DriverClickListener, ActiveClickListener {

    private lateinit var binding: AssociationBinding
    private val currentViewViewModel: CurrentViewViewModel by activityViewModels()
    private val verifiedViewModel: VerifiedViewModel by activityViewModels()
    private lateinit var filteredActive: ArrayList<RideDriver>
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private lateinit var filteredDriver: ArrayList<User>
    private lateinit var active: ArrayList<RideDriver>
    private lateinit var dbRef: DatabaseReference
    private lateinit var drivers: ArrayList<User>
    private lateinit var rvDrivers: RecyclerView
    private lateinit var rvActive: RecyclerView
    private lateinit var auth: FirebaseAuth
    private lateinit var account: Account
    private lateinit var user: User

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialize()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AssociationBinding.inflate(inflater, container, false)
        rvDrivers = LayoutInflater.from(requireContext()).inflate(R.layout.rv_drivers, binding.root, false) as RecyclerView
        rvActive = LayoutInflater.from(requireContext()).inflate(R.layout.rv_active, binding.root, false) as RecyclerView
        binding.vpViews.adapter = TabAdapter(listOf(rvDrivers, rvActive))
        binding.vpViews.isUserInputEnabled = false
        binding.vpViews.offscreenPageLimit = 2
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fetchAPI {
            fetchData(false)
        }
        setupTabs()

        if (user.emailVerified == false || account.verified == false)
            verifiedViewModel.isVerified.observe(viewLifecycleOwner) { verified ->
                if (verified) binding.restrictUnverified.visibility = View.GONE
                else binding.restrictUnverified.visibility = View.VISIBLE
            }

        rvDrivers.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(activity)
        }

        rvActive.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(activity)
        }

        binding.swipeRefresh.setColorSchemeResources(R.color.blue, R.color.red)
        binding.swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.progress)

        binding.swipeRefresh.setOnRefreshListener {
            fetchData(true)
        }

        binding.searchBar.setOnQueryTextListener(searchListener())

        binding.filterDrivers.setOnCheckedChangeListener { _, checked ->
            filterDrivers(checked)
        }
    }

    override fun onResume() {
        super.onResume()
        currentViewViewModel.setCurrentView(requireView())
        binding.tab.selectTab(binding.tab.getTabAt(Global.tabPosition))
        if (Global.tabPosition == 0) binding.filterDrivers.visibility = View.VISIBLE
        else binding.filterDrivers.visibility = View.GONE
    }

    override fun onDriverClick(user: User) {
        Global.profiledUser = user
        findNavController().navigate(HomeDirections.actionHome2ToDriverProfile())
    }

    override fun onActiveClick(user: RideDriver) {
        Global.activeUser = user
        val intent = Intent(requireContext(), RideActivity::class.java).apply {
            putExtra("VIEW", true)
        }
        startActivity(intent)
    }

    private fun initialize() {
        auth = Firebase.auth
        dbRef = Firebase.database.reference
        remoteConfig = Firebase.remoteConfig
        user = Global.user!!
        account = Global.account!!
        drivers = arrayListOf()
        active = arrayListOf()
        filteredDriver = arrayListOf()
        filteredActive = arrayListOf()
    }

    private fun fetchAPI(onComplete: (() -> Unit)? = null) {
        setupProgress()
        remoteConfig.fetchAndActivate().addOnCompleteListener(requireActivity()) { task ->
            if (task.isSuccessful) Global.key = remoteConfig.getString("maps_api_key")
            onComplete?.invoke()
        }
    }

    private fun fetchData(isRefresh: Boolean) {
        setupProgress()
        getCity(account.route!!.substringBefore('-').toLatLng()) { success ->
            if (success) {
                if (isRefresh) {
                    when (Global.tabPosition) {
                        0 -> {
                            fetchDrivers {
                                setupData {
                                    if (isRefresh) binding.swipeRefresh.isRefreshing = false
                                    endProgress()
                                }
                            }
                        }
                        1 -> {
                            fetchActive {
                                setupData {
                                    if (isRefresh) binding.swipeRefresh.isRefreshing = false
                                    endProgress()
                                }
                            }
                        }
                    }
                } else {
                    fetchDrivers {
                        fetchActive {
                            setupData {
                                if (isRefresh) binding.swipeRefresh.isRefreshing = false
                                endProgress()
                            }
                        }
                    }
                }
            } else fetchData(isRefresh)
        }
    }

    private fun getCity(latLng: LatLng, onComplete: (Boolean) -> Unit) {
        RetrofitClient.geocodeService.reverseGeocode(latLng.toLatLngString(), Global.key!!)
            .enqueue(object : Callback<GeocodeResponse> {
                override fun onResponse(call: Call<GeocodeResponse?>, response: Response<GeocodeResponse?>) {
                    if (response.isSuccessful) {
                        Global.city = response.body()?.toLocalAddress()!!.city
                        onComplete(true)
                    } else onComplete(false)
                }
                override fun onFailure(call: Call<GeocodeResponse?>, t: Throwable) {
                    onComplete(false)
                }
            })
    }

    private fun fetchActive(onComplete: () -> Unit) {
        dbRef.child("/Ride/${Global.city}/Driver").orderByChild("assoc").equalTo(user.uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    active.clear()
                    if (snapshot.exists()) {
                        for (user in snapshot.children) {
                            val driver = user.getValue(RideDriver::class.java)
                            if (driver?.active == true) {
                                driver.name = drivers.firstOrNull { it.uid == user.key }?.name
                                driver.plate = drivers.firstOrNull { it.uid == user.key }?.plate
                                driver.profile = drivers.firstOrNull { it.uid == user.key }?.profile
                                active.add(driver)
                            }
                        }
                    }
                    onComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                    onComplete()
                }
            })
    }

    private fun fetchDrivers(onComplete: () -> Unit) {
        dbRef.child("TodaGoDrive/Driver").orderByChild("assoc").equalTo(user.uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    drivers.clear()
                    if (snapshot.exists()) {
                        for (user in snapshot.children)
                            drivers.add(user.getValue(User::class.java)!!)
                    }
                    onComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                    onComplete()
                }
            })
    }

    private fun setupData(onComplete: () -> Unit) {
        if (!Global.searching) {
            rvDrivers.adapter = DriverAdapter(drivers, this)
            rvActive.adapter = ActiveAdapter(active, this)
        } else filterDataOnSubmit(binding.searchBar.query.toString().trim())
        onComplete()
    }

    private fun setupTabs() {
        binding.tab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                Global.tabPosition = tab!!.position
                if (tab.position == 0) binding.filterDrivers.visibility = View.VISIBLE
                else binding.filterDrivers.visibility = View.GONE
                clear()
                resetData {
                    binding.vpViews.currentItem = tab.position
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        binding.tab.getTabAt(0)!!.view.setOnLongClickListener {
            true
        }
        binding.tab.getTabAt(1)!!.view.setOnLongClickListener {
            true
        }
    }

    private fun resetData(onComplete: () -> Unit) {
        if (Global.searching) {
            Global.searching = false
            binding.searchBar.setQuery(null, false)
            Handler(Looper.getMainLooper()).postDelayed({
                onComplete()
            }, 50)
        } else onComplete()
    }

    private fun searchListener(): SearchView.OnQueryTextListener {
        return object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                clear()
                filterDataOnSubmit(query!!.trim())
                return true
            }
            override fun onQueryTextChange(query: String?): Boolean {
                resetDataOnClear(query)
                return true
            }
        }
    }

    private fun resetDataOnClear(query: String?) {
        if (query.isNullOrBlank()) {
            when (Global.tabPosition) {
                0 -> rvDrivers.adapter = DriverAdapter(drivers, this)
                1 -> rvActive.adapter = ActiveAdapter(active, this)
            }
        }
    }

    private fun filterDrivers(checked: Boolean) {
        if (checked) {
            filteredDriver.clear()
            filteredDriver.addAll(drivers.filter { driver ->
                driver.verified == false
            })
            rvDrivers.adapter = DriverAdapter(filteredDriver, this)
        } else rvDrivers.adapter = DriverAdapter(drivers, this)
    }

    private fun filterDataOnSubmit(query: String?) {
        filteredDriver.clear()
        filteredActive.clear()
        if (!query.isNullOrBlank()) {
            Global.searching = true
            when (Global.tabPosition) {
                0 -> {
                    filteredDriver.addAll(drivers.filter { driver ->
                        driver.name!!.contains(query, true) ||
                                driver.phone!!.contains(query, true) ||
                                driver.plate!!.contains(query, true) ||
                                driver.email!!.contains(query, true)
                    })
                    rvDrivers.adapter = DriverAdapter(filteredDriver, this)
                }
                1 -> {
                    filteredActive.addAll(active.filter { driver ->
                        driver.name!!.contains(query, true) ||
                                driver.phone!!.contains(query, true) ||
                                driver.plate!!.contains(query, true)
                    })
                    rvActive.adapter = ActiveAdapter(filteredActive, this)
                }
            }
        }
    }

    private fun clear() {
        binding.root.clearFocus()
        hideKeyboard()
    }

    private fun setupProgress(message: String = "Loading data") {
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