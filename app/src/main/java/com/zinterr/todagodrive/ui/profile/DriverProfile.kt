package com.zinterr.todagodrive.ui.profile

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.*
import androidx.fragment.app.Fragment
import android.view.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.*
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.google.firebase.Firebase
import com.google.firebase.auth.*
import com.google.firebase.database.*
import com.google.firebase.remoteconfig.*
import com.google.firebase.storage.*
import com.squareup.picasso.Picasso
import com.zinterr.todagodrive.R
import com.zinterr.todagodrive.LoginActivity
import com.zinterr.todagodrive.TodaGoDrive
import com.zinterr.todagodrive.databinding.DriverProfileBinding
import com.zinterr.todagodrive.model.*
import com.zinterr.todagodrive.model.Global.getUriBytes
import com.zinterr.todagodrive.model.Global.setOnDebouncedClickListener
import com.zinterr.todagodrive.model.Global.toLatLngString
import com.zinterr.todagodrive.model.Global.toLocalAddress
import com.zinterr.todagodrive.network.RetrofitClient
import com.zinterr.todagodrive.ui.home.Home
import com.zinterr.todagodrive.ui.popup.*
import com.zinterr.todagodrive.util.*
import com.zinterr.todagodrive.viewmodel.*
import retrofit2.*

@SuppressLint("SetTextI18n")
class DriverProfile : Fragment() {

    private lateinit var binding: DriverProfileBinding
    private val currentViewViewModel: CurrentViewViewModel by activityViewModels()
    private val verifiedViewModel: VerifiedViewModel by activityViewModels()
    private val busyViewModel: BusyViewModel by activityViewModels()
    private var assocPickerDialog: AssocPickerDialog? = null
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private var passwordDialog: PasswordDialog? = null
    private var confirmDialog: ConfirmDialog? = null
    private lateinit var session: SessionViewModel
    private lateinit var dbRef: DatabaseReference
    private lateinit var stRef: StorageReference
    private var emailDialog: EmailDialog? = null
    private var phoneDialog: PhoneDialog? = null
    private var infoDialog: InfoDialog? = null
    private lateinit var auth: FirebaseAuth
    private lateinit var account: Account
    private lateinit var user: User
    private var isAssoc = false
    private val getImage = registerForActivityResult(ActivityResultContracts.GetContent()) {
        if (it != null) uploadImage(it) }
    val callbackFalse = object : OnBackPressedCallback(true) { override fun handleOnBackPressed() {} }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialize()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DriverProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fetchAPI {
            setupData()
        }

        busyViewModel.isBusy.observe(viewLifecycleOwner) { busy ->
            binding.btnEditAccount.isEnabled = !busy
            binding.btnLogout.isEnabled = !busy
            binding.btnDelete.isEnabled = !busy
        }

        binding.loading.progress.setIndicatorColor(
            ContextCompat.getColor(requireContext(), R.color.blue_dark),
            ContextCompat.getColor(requireContext(), R.color.red))

        if (isAssoc) {
            binding.headerTitle.visibility = View.VISIBLE
            binding.main.setBackgroundResource(R.drawable.background)
        }

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnEditProfile.setOnDebouncedClickListener {
            editProfile()
        }

        binding.btnEditEmail.setOnDebouncedClickListener {
            editEmail()
        }

        binding.btnVerifyEmail.setOnDebouncedClickListener {
            verifyEmail()
        }

        binding.btnEditPhone.setOnDebouncedClickListener {
            editPhone()
        }

        binding.btnEditAssoc.setOnDebouncedClickListener {
            editAssoc()
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

        binding.btnVerify.setOnDebouncedClickListener {
            verifyMember()
        }

        binding.btnRemove.setOnDebouncedClickListener {
            removeMember()
        }

        binding.swipeRefresh.setColorSchemeResources(R.color.blue, R.color.red)
        binding.swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.progress)

        binding.swipeRefresh.setOnRefreshListener {
            setupData()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isAssoc) currentViewViewModel.setCurrentView(requireView())
    }

    override fun onDestroy() {
        super.onDestroy()
        if (session.profiledUser.value != null) session.setProfiledUser(null)
        if (confirmDialog != null) if (confirmDialog!!.isAdded) confirmDialog!!.dismiss()
        if (passwordDialog != null) if (passwordDialog!!.isAdded) passwordDialog!!.dismiss()
        if (emailDialog != null) if (emailDialog!!.isAdded) emailDialog!!.dismiss()
        if (infoDialog != null) if (infoDialog!!.isAdded) infoDialog!!.dismiss()
        if (phoneDialog != null) if (phoneDialog!!.isAdded) phoneDialog!!.dismiss()
        if (assocPickerDialog != null) if (assocPickerDialog!!.isAdded) assocPickerDialog!!.dismiss()
    }

    private fun initialize() {
        auth = Firebase.auth
        dbRef = Firebase.database.reference
        remoteConfig = Firebase.remoteConfig
        stRef = Firebase.storage.reference
        session = ViewModelProvider((requireActivity().application as TodaGoDrive),
            ViewModelProvider.AndroidViewModelFactory
                .getInstance((requireActivity().application as TodaGoDrive)))[SessionViewModel::class.java]
        isAssoc = session.profiledUser.value != null
        account = session.account.value!!
        user = if (isAssoc) session.profiledUser.value!! else session.user.value!!
    }

    private fun fetchAPI(onComplete: (() -> Unit)? = null) {
        setupProgress("Loading data")
        remoteConfig.fetchAndActivate().addOnCompleteListener(requireActivity()) { task ->
            if (task.isSuccessful) session.setKey(remoteConfig.getString("maps_api_key"))
            onComplete?.invoke()
        }
    }

    private fun setupData() {
        setupProgress("Loading data")
        if (user.profile.isNullOrBlank()) binding.userImage.setImageResource(R.drawable.tricycle)
        else Picasso.get()
            .load(user.profile)
            .placeholder(R.drawable.tricycle)
            .into(binding.userImage)
        binding.name.text = user.name
        binding.verified.setImageResource(if (user.verified!!) R.drawable.verified else R.drawable.unverified)
        binding.stars.rating = user.rating!!/user.rates!!.toFloat()
        binding.rating.text = "${if ((user.rating!! / user.rates!!.toFloat()).isNaN()) 0F else "%.2f".format(user.rating!! / user.rates!!.toFloat())} (${user.rates})"
        if (!isAssoc) {
            if (user.verified!! && (user.permit != null || user.license != null)) {
                dbRef.child("TodaGoDrive/Driver/${user.uid}").updateChildren(hashMapOf("/permit" to null, "/license" to null))
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) stRef.child("${user.uid}").delete()
                        session.user.value?.let { user ->
                            user.apply {
                                license = null
                                permit = null
                            }
                            session.setUser(user)
                        }
                        user = session.user.value!!
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
                            dbRef.child("TodaGoDrive/Driver/${user.uid}/email").setValue(it)
                        }
                    }
                    auth.currentUser!!.isEmailVerified.let {
                        if (it != user.emailVerified) {
                            dbRef.child("TodaGoDrive/Driver/${account.uid}")
                                .runTransaction(object : Transaction.Handler {
                                    override fun doTransaction(currentData: MutableData): Transaction.Result {
                                        val rating = currentData.child("rating").getValue(Float::class.java)
                                        val rates = currentData.child("rates").getValue(Int::class.java)
                                        if (rating != null && rates != null) {
                                            if (rates < 2) {
                                                currentData.child("rating").value = if (rates == 1) rating + 5F else 2.5F
                                                currentData.child("rates").value = rates + 1
                                            }
                                        }
                                        currentData.child("emailVerified").value = it
                                        return Transaction.success(currentData)
                                    }
                                    override fun onComplete(error: DatabaseError?,
                                                            committed: Boolean, currentData: DataSnapshot?
                                    ) {
                                        if (committed) {
                                            session.user.value?.let { user ->
                                                user.apply {
                                                    if ((rates ?: 0) < 2) {
                                                        rating = if (rates == 1) (rating ?: 0F) + 5F else 2.5F
                                                        rates = (rates ?: 0) + 1
                                                    }
                                                    emailVerified = true
                                                }
                                                session.setUser(user)
                                            }
                                            user = session.user.value!!
                                            binding.stars.rating = user.rating!!/user.rates!!.toFloat()
                                            binding.rating.text = "${if ((user.rating!! / user.rates!!.toFloat()).isNaN()) 0F else user.rating!!/user.rates!!.toFloat()} (${user.rates})"
                                            binding.btnVerifyEmail.visibility = if (user.emailVerified == true) View.GONE else View.VISIBLE
                                            binding.emailVerified.text = if (user.emailVerified == true) "Verified" else "Unverified"
                                            if (user.verified == true) verifiedViewModel.setVerified(true)
                                        }
                                    }
                                })
                        }
                    }
                }
            if (user.emailVerified == true && user.verified == true) verifiedViewModel.setVerified(true)
            binding.btnVerifyEmail.visibility = if (user.emailVerified == true) View.GONE else View.VISIBLE
            binding.driver.visibility = View.VISIBLE
        } else {
            binding.btnRemove.visibility = View.VISIBLE
            binding.btnVerify.visibility = if (user.verified!!) View.GONE else View.VISIBLE
        }
        binding.email.text = user.email
        binding.phone.text = user.phone
        binding.emailVerified.text = if (user.emailVerified == true) "Verified" else "Unverified"
        if (isAssoc) {
            binding.assoc.text = account.name
        } else getAssoc { name ->
            binding.assoc.text = name
        }
        binding.swipeRefresh.isRefreshing = false
        setupLocation()
    }

    private fun setupLocation() {
        RetrofitClient.geocodeService.reverseGeocode("${user.location}", session.key.value!!)
            .enqueue(object : Callback<GeocodeResponse> {
                override fun onResponse(call: Call<GeocodeResponse?>, response: Response<GeocodeResponse?>) {
                    if (response.isSuccessful) {
                        val address = response.body()?.toLocalAddress()!!
                        binding.location.text = (if (address.extra != null) "${address.extra}, " else "") +
                                (if (address.locality != null) "${address.locality}, " else "") +
                                (if (address.city != null) "${address.city}, " else "") +
                                (address.province ?: "")
                    } else snackBar(view, "Error: ${response.errorBody()}")
                    endProgress()
                }
                override fun onFailure(call: Call<GeocodeResponse?>, t: Throwable) {
                    endProgress()
                    snackBar(view, "Error: ${t.message}")
                }
            })
    }

    private fun getAssoc(onComplete: (String) -> Unit) {
        dbRef.child("Account/TodaGoDrive/${user.assoc}/name")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val name = snapshot.getValue(String::class.java)!!
                        onComplete(name)
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                }
            })
    }

    private fun editProfile() {
        confirmDialog = ConfirmDialog("Edit profile",
            "Do you want to upload a new profile?",
            "Upload", "Cancel") { confirm ->
            if (confirm) getImage.launch("image/*")
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun uploadImage(uri: Uri) {
        setupProgress("Uploading profile")
        val bytes = getUriBytes(requireContext(), uri, 100_000)
        bytes.let { byte ->
            stRef.child("${account.uid}/profile.jpg").putBytes(byte)
                .addOnSuccessListener { uploadTask ->
                    uploadTask.metadata!!.reference!!.downloadUrl
                        .addOnSuccessListener { url ->
                            dbRef.child("TodaGoDrive/Driver/${account.uid}/profile").setValue(url)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        snackBar(view, "Profile upload complete")
                                        user.profile = "$url"
                                        Picasso.get().load(user.profile).placeholder(R.drawable.tricycle)
                                            .into(binding.userImage)
                                    }
                                    else snackBar(view, "Error: ${task.exception?.message}")
                                    endProgress()
                                }
                        }
                }
                .addOnFailureListener {
                    snackBar(view, "Error: ${it.message}")
                    endProgress()
                }
        }
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

    private fun editAssoc() {
        confirmDialog = ConfirmDialog("Change association",
            "Are you sure you want to change your Association? Please be advised that " +
                    "you will not be able to accept rides until your new Association verifies you as their member.",
            "Edit Association", "Cancel") { confirm ->
            if (confirm) openAssocPicker()
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun openAssocPicker() {
        assocPickerDialog = AssocPickerDialog(session.key.value!!) { assocUID, assocName, latLng, address ->
            snackBar(view, "Your Location and Association has been changed")
            session.user.value?.let { user ->
                user.apply {
                    assoc = assocUID
                    location = latLng.toLatLngString()
                    verified = false
                }
                session.setUser(user)
            }
            user = session.user.value!!
            binding.assoc.text = assocName
            binding.location.text = (if (address.extra != null) "${address.extra}, " else "") +
                    (if (address.locality != null) "${address.locality}, " else "") +
                    (if (address.city != null) "${address.city}, " else "") +
                    (address.province ?: "")
            binding.verified.setImageResource(R.drawable.unverified)
            verifiedViewModel.setVerified(false)
            editAccount()
        }
        assocPickerDialog!!.show(childFragmentManager, "AssocPickerDialog")
    }

    private fun editAccount() {
        binding.btnEditProfile.apply {
            visibility = if (isGone) View.VISIBLE
            else View.GONE
        }
        binding.btnEditEmail.apply {
            visibility = if (isGone) View.VISIBLE
            else View.GONE
        }
        binding.btnEditPhone.apply {
            visibility = if (isGone) View.VISIBLE
            else View.GONE
        }
        binding.btnEditAccount.apply {
            val btnTxt = "Edit Account Details"
            text = if (text.equals(btnTxt)) "Cancel Edit" else btnTxt
        }
    }

    private fun validate() {
        val updates = hashMapOf<String, Any>(
            "/verified" to true,
            "/rating" to if (user.rates!! < 2)
                if (user.rates!! == 1) user.rating!!.plus(5F)
                else 2.5F
            else user.rating!!.plus(5F),
            "/rates" to user.rates!! + 1)
        dbRef.child("TodaGoDrive/Driver/${user.uid}").updateChildren(updates)
            .addOnSuccessListener {
                snackBar(view, "You have verified this member")
                user.verified = true
                user.rating = if (user.rates!! < 2) {
                    if (user.rates!! == 1) user.rating!!.plus(5F)
                    else 2.5F
                } else user.rating!!.plus(5F)
                user.rates = user.rates!! + 1
                session.setProfiledUser(user)
                setupData()
            }
            .addOnFailureListener {
                snackBar(view, "Error: ${it.message}")
            }
    }

    private fun remove() {
        dbRef.child("TodaGoDrive/Driver/${user.uid}/assoc").setValue(null)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    snackBar(view, "You have removed this user as your member")
                    findNavController().popBackStack()
                } else snackBar(view, "Error: ${task.exception?.message}")
            }
    }

    private fun verifyMember() {
        confirmDialog = ConfirmDialog("Verify as member",
            "Please confirm this member account validation", "Validate account",
            "Cancel", images = Pair(user.license, user.permit)) { confirm ->
            if (confirm) validate()
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun removeMember() {
        confirmDialog = ConfirmDialog("Remove member",
            "Are you sure you want to remove this member?",
            "Remove", "Cancel") { confirm ->
            if (confirm) remove()
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun changePassword() {
        passwordDialog = PasswordDialog(user.email!!) {
            snackBar(view, "Password has been changed")
        }
        passwordDialog!!.show(childFragmentManager, "ResetDialog")
    }

    private fun delete() {
        setupProgress("Processing")
        auth.currentUser!!.delete()
            .addOnCompleteListener { task ->
                endProgress()
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
        if (!isAssoc) Home().addCallbackFalse()
        else requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callbackFalse)
    }

    private fun endProgress() {
        binding.loading.container.visibility = View.GONE
        if (!isAssoc) Home().addCallbackTrue()
        else callbackFalse.remove()
    }
}