package com.zinterr.todagodrive.ui.profile

import android.annotation.SuppressLint
import android.content.Intent
import android.os.*
import androidx.fragment.app.Fragment
import android.view.*
import androidx.core.content.ContextCompat
import androidx.core.view.*
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.Firebase
import com.google.firebase.auth.*
import com.google.firebase.database.*
import com.google.firebase.remoteconfig.*
import com.google.firebase.storage.*
import com.zinterr.todagodrive.LoginActivity
import com.zinterr.todagodrive.R
import com.zinterr.todagodrive.TodaGoDrive
import com.zinterr.todagodrive.databinding.AssociationProfileBinding
import com.zinterr.todagodrive.model.*
import com.zinterr.todagodrive.model.Global.setOnDebouncedClickListener
import com.zinterr.todagodrive.model.Global.toLocalAddress
import com.zinterr.todagodrive.network.RetrofitClient
import com.zinterr.todagodrive.ui.home.Home
import com.zinterr.todagodrive.ui.popup.*
import com.zinterr.todagodrive.util.*
import com.zinterr.todagodrive.viewmodel.*
import retrofit2.*

// ADD FARE MATRIX UPDATE
@SuppressLint("SetTextI18n")
class AssociationProfile : Fragment() {

    private lateinit var binding: AssociationProfileBinding
    private val currentViewViewModel: CurrentViewViewModel by activityViewModels()
    private val viewModel: VerifiedViewModel by activityViewModels()
    private var routePickerDialog: RoutePickerDialog? = null
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private var passwordDialog: PasswordDialog? = null
    private var confirmDialog: ConfirmDialog? = null
    private lateinit var session: SessionViewModel
    private lateinit var dbRef: DatabaseReference
    private var emailDialog: EmailDialog? = null
    private var phoneDialog: PhoneDialog? = null
    private lateinit var stRef: StorageReference
    private var infoDialog: InfoDialog? = null
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
        binding = AssociationProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fetchAPI {
            setupData()
        }

        binding.loading.progress.setIndicatorColor(
            ContextCompat.getColor(requireContext(), R.color.blue_dark),
            ContextCompat.getColor(requireContext(), R.color.red))

        binding.btnEditEmail.setOnDebouncedClickListener {
            editEmail()
        }

        binding.btnVerifyEmail.setOnDebouncedClickListener {
            verifyEmail()
        }

        binding.btnEditPhone.setOnDebouncedClickListener {
            editPhone()
        }

        binding.btnEditRoute.setOnDebouncedClickListener {
            editRoute()
        }

        binding.btnEditAccount.setOnClickListener {
            editAccount()
        }

        binding.btnPassword.setOnDebouncedClickListener {
            changePassword()
        }

        binding.btnDelete.setOnDebouncedClickListener {
            deleteAccount()
        }

        binding.btnLogout.setOnDebouncedClickListener {
            signOut()
        }

        binding.swipeRefresh.setColorSchemeResources(R.color.blue, R.color.red)
        binding.swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.progress)

        binding.swipeRefresh.setOnRefreshListener {
            setupData()
        }
    }

    override fun onResume() {
        super.onResume()
        currentViewViewModel.setCurrentView(requireView())
    }

    override fun onDestroy() {
        super.onDestroy()
        if (passwordDialog != null) if (passwordDialog!!.isAdded) passwordDialog!!.dismiss()
        if (confirmDialog != null) if (confirmDialog!!.isAdded) confirmDialog!!.dismiss()
        if (emailDialog != null) if (emailDialog!!.isAdded) emailDialog!!.dismiss()
        if (infoDialog != null) if (infoDialog!!.isAdded) infoDialog!!.dismiss()
        if (phoneDialog != null) if (phoneDialog!!.isAdded) phoneDialog!!.dismiss()
        if (routePickerDialog != null) if (routePickerDialog!!.isAdded) routePickerDialog!!.dismiss()
    }

    private fun initialize() {
        auth = Firebase.auth
        dbRef = Firebase.database.reference
        remoteConfig = Firebase.remoteConfig
        stRef = Firebase.storage.reference
        session = ViewModelProvider((requireActivity().application as TodaGoDrive),
            ViewModelProvider.AndroidViewModelFactory
                .getInstance((requireActivity().application as TodaGoDrive)))[SessionViewModel::class.java]
        user = session.user.value!!
        account = session.account.value!!
    }

    private fun fetchAPI(onComplete: (() -> Unit)? = null) {
        setupProgress("Loading data")
        remoteConfig.fetchAndActivate().addOnCompleteListener(requireActivity()) { task ->
            if (task.isSuccessful) session.setKey(remoteConfig.getString("maps_api_key"))
            endProgress()
            onComplete?.invoke()
        }
    }

    private fun setupData() {
        setupProgress("Loading data")
        binding.name.text = account.name
        binding.verified.setImageResource(if (account.verified!!) R.drawable.verified else R.drawable.unverified)
        if (account.verified!! && user.document != null) {
            dbRef.child("TodaGoDrive/Association/${account.uid}/document").setValue(null)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) stRef.child("${account.uid}").delete()
                    session.user.value?.let { user ->
                        user.document = null
                        session.setUser(user)
                    }
                    user.document = null
                }
        }
        auth.currentUser!!.reload()
            .addOnCompleteListener {
                auth.currentUser!!.email.let {
                    if (it != user.email) {
                        session.user.value?.let { user ->
                            user.email = it
                            session.setUser(user)
                        }
                        user.email = it
                        dbRef.child("TodaGoDrive/Association/${user.uid}/email").setValue(it)
                    }
                }
                auth.currentUser!!.isEmailVerified.let {
                    if (it != user.emailVerified) {
                        dbRef.child("TodaGoDrive/Association/${user.uid}/emailVerified").setValue(it)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    session.user.value?.let { user ->
                                        user.emailVerified = true
                                        session.setUser(user)
                                    }
                                    user.emailVerified = true
                                    if (account.verified == true) viewModel.setVerified(true)
                                    binding.emailVerified.text = if (user.emailVerified == true) "Verified" else "Unverified"
                                    binding.btnVerifyEmail.visibility = if (user.emailVerified == true) View.GONE else View.VISIBLE
                                }
                            }
                    }
                }
            }
        if (user.emailVerified == true && account.verified == true) viewModel.setVerified(true)
        binding.emailVerified.text = if (user.emailVerified == true) "Verified" else "Unverified"
        binding.btnVerifyEmail.visibility = if (user.emailVerified == true) View.GONE else View.VISIBLE
        binding.email.text = user.email
        binding.phone.text = user.phone
        binding.swipeRefresh.isRefreshing = false
        setupLocation()
    }

    private fun setupLocation() {
        setupProgress("Loading data")
        val start = account.route!!.substringBefore('-')
        val end = if (account.route!!.contains('-')) account.route!!.substringAfter('-') else null
        RetrofitClient.geocodeService.reverseGeocode(start, session.key.value!!)
            .enqueue(object : Callback<GeocodeResponse> {
                override fun onResponse(call: Call<GeocodeResponse?>, response: Response<GeocodeResponse?>) {
                    if (response.isSuccessful) {
                        val first = response.body()?.toLocalAddress()!!
                        if (end != null) {
                            RetrofitClient.geocodeService.reverseGeocode(end, session.key.value!!)
                                .enqueue(object : Callback<GeocodeResponse> {
                                    override fun onResponse(call: Call<GeocodeResponse?>, response: Response<GeocodeResponse?>) {
                                        if (response.isSuccessful) {
                                            val second = response.body()?.toLocalAddress()!!
                                            binding.route.text = "${first.locality}-${second.locality} | ${first.city}, ${first.province}"
                                        } else snackBar(view, "Error: ${response.errorBody()}")
                                        endProgress()
                                    }
                                    override fun onFailure(call: Call<GeocodeResponse?>, t: Throwable) {}
                                })
                        } else binding.route.text = "${first.locality}, ${first.city}, ${first.province}"
                    } else snackBar(view, "Error: ${response.errorBody()}")
                    endProgress()
                }
                override fun onFailure(call: Call<GeocodeResponse?>, t: Throwable) {
                    endProgress()
                    snackBar(view, "Error: ${t.message}")
                }
            })
    }

    private fun editEmail() {
        emailDialog = EmailDialog {
            infoDialog = InfoDialog("Email verification links has been sent to your " +
                    "current and new email address. Please click the links to confirm your email change.")
            infoDialog!!.show(childFragmentManager, "InfoDialog")
            editAccount()
        }
        emailDialog!!.show(childFragmentManager, "EmailDialog")
    }

    private fun verifyEmail() {
        setupProgress("Sending verification link")
        auth.currentUser!!.sendEmailVerification()
            .addOnSuccessListener {
                infoDialog = InfoDialog("Email verification link has been sent to your " +
                        "email address. Please click the link to confirm your email change. If you " +
                        "can't find it in your Inbox, kindly check your Spam folder.")
                infoDialog!!.show(childFragmentManager, "InfoDialog")
                endProgress()
            }
            .addOnFailureListener {
                endProgress()
                snackBar(view, "Error: ${it.message}")
            }
    }

    private fun editPhone() {
        phoneDialog = PhoneDialog(account.type!!) { phone ->
            snackBar(view, "Phone number has been changed")
            session.user.value?.let { user ->
                user.phone = phone
                session.setUser(user)
            }
            user.phone = phone
            binding.phone.text = user.phone
            editAccount()
        }
        phoneDialog!!.show(childFragmentManager, "PhoneDialog")
    }

    private fun editRoute() {
        confirmDialog = ConfirmDialog("Change location",
            "Are you sure you want to change your route?",
            "Change", "Cancel") { confirm ->
            if (confirm) {
                routePickerDialog = RoutePickerDialog(session.key.value!!) { start, startAddress, end, endAddress ->
                    setupProgress("Saving changes")
                    dbRef.child("Account/TodaGoDrive/${account.uid}/route")
                        .setValue("${start.latitude},${start.longitude}${if (end != null) 
                            "-${end.latitude},${end.longitude}" else null}")
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                session.account.value?.let { acc ->
                                    acc.route = "${start.latitude},${start.longitude}${if (end != null)
                                        "-${end.latitude},${end.longitude}" else null}"
                                    session.setAccount(acc)
                                }
                                account.route = "${start.latitude},${start.longitude}${if (end != null)
                                    "-${end.latitude},${end.longitude}" else null}"
                                if (end != null) binding.route.text = "${startAddress.locality}-" +
                                        "${endAddress!!.locality} | ${startAddress.city}, ${startAddress.province}"
                                else binding.route.text = "${startAddress.locality}, ${startAddress.city}, ${startAddress.province}"
                                editAccount()
                                endProgress()
                            } else {
                                endProgress()
                                snackBar(view, "Error: ${task.exception!!.message}")
                            }
                        }
                }
                routePickerDialog!!.show(childFragmentManager, "RoutePickerDialog")
            }
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun editAccount() {
        binding.btnEditEmail.apply {
            visibility = if (isGone) View.VISIBLE
            else View.GONE
        }
        binding.btnEditPhone.apply {
            visibility = if (isGone) View.VISIBLE
            else View.GONE
        }
        binding.btnEditRoute.apply {
            visibility = if (isGone) View.VISIBLE
            else View.GONE
        }
        binding.btnEditAccount.apply {
            val btnTxt = "Edit Account Details"
            text = if (text.equals(btnTxt)) "Cancel Edit" else btnTxt
        }
    }

    private fun changePassword() {
        passwordDialog = PasswordDialog(user.email!!) {
            snackBar(view, "Password has been changed")
        }
        passwordDialog!!.show(childFragmentManager, "ResetDialog")
    }

    private fun delete() {
        auth.currentUser!!.delete()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val intent = Intent(requireContext(), LoginActivity::class.java)
                    startActivity(intent)
                    requireActivity().finish()
                }
            }
    }

    private fun signOut() {
        confirmDialog = ConfirmDialog("Log out",
            "Are you sure you want to log out?",
            "Log out", "Cancel") { confirm ->
            if (confirm) {
                auth.signOut()
                val intent = Intent(requireContext(), LoginActivity::class.java)
                startActivity(intent)
                requireActivity().finish()
            }
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun deleteAccount() {
        confirmDialog = ConfirmDialog("Delete account",
            "Are you sure you want to delete your account?",
            "Delete Account", "Cancel") { confirm ->
            if (confirm) delete()
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
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