package com.zinterr.todagodrive.ui.login

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.text.*
import android.util.Patterns
import androidx.fragment.app.Fragment
import android.view.*
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.view.*
import androidx.navigation.fragment.findNavController
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Firebase
import com.google.firebase.auth.*
import com.google.firebase.database.*
import com.google.firebase.remoteconfig.*
import com.google.firebase.storage.*
import com.zinterr.todagodrive.*
import com.zinterr.todagodrive.databinding.SignupAssociationBinding
import com.zinterr.todagodrive.model.*
import com.zinterr.todagodrive.model.Global.createImageUri
import com.zinterr.todagodrive.model.Global.getUriBytes
import com.zinterr.todagodrive.model.Global.hideKeyboard
import com.zinterr.todagodrive.model.Global.setOnDebouncedClickListener
import com.zinterr.todagodrive.model.Global.toLocalAddress
import com.zinterr.todagodrive.network.RetrofitClient
import com.zinterr.todagodrive.util.*
import retrofit2.*
import java.util.UUID

@SuppressLint("ClickableViewAccessibility, SetTextI18n")
class SignupAssociation : Fragment() {

    private lateinit var binding: SignupAssociationBinding
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private lateinit var dbRef: DatabaseReference
    private lateinit var stRef: StorageReference
    private lateinit var auth: FirebaseAuth
    private var textView: TextView? = null
    private var typeImage: String? = null
    private var route: String? = null
    private var start: LatLng? = null
    private var phone: String? = null
    private var email: String? = null
    private var document: Uri? = null
    private var name: String? = null
    private var pass: String? = null
    private var type: String? = null
    private var end: LatLng? = null
    private var matrix: Uri? = null
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) getImage() else requireCamera() }
    private val getImage = registerForActivityResult(ActivityResultContracts.TakePicture()) { if (it) setImage() }
    private val getLocation = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) { if (Global.key == null) fetchApi { showData(result.data)
            } else showData(result.data) } }
    private val callbackFalse = object : OnBackPressedCallback(true) { override fun handleOnBackPressed() {} }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialize()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SignupAssociationBinding.inflate(inflater, container, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.signupAssociation) { _, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val params = binding.btnSign.layoutParams as ViewGroup.MarginLayoutParams
            params.bottomMargin = 40 + navBars.bottom
            binding.btnSign.layoutParams = params
            insets
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        emailListener()
        passwordListener()
        phoneListener()
        fetchApi {}

        binding.root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_UP -> clear()
            }
            true
        }

        binding.scroller.setOnDebouncedClickListener {
            clear()
        }

        binding.loading.progress.setIndicatorColor(
            "#1561FF".toColorInt(),
            "#FFBF0D3E".toColorInt())

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.locationPickerStart.setOnDebouncedClickListener {
            getAddress(binding.locationStart, "START")
        }

        binding.locationPickerEnd.setOnDebouncedClickListener {
            getAddress(binding.locationEnd, "END")
        }

        binding.docPicker.setOnDebouncedClickListener {
            selectImage("DOC")
        }

        binding.matrixPicker.setOnDebouncedClickListener {
            selectImage("MATRIX")
        }

        binding.btnSign.setOnDebouncedClickListener {
            signUp()
        }
    }

    private fun initialize() {
        dbRef = Firebase.database.reference
        stRef = Firebase.storage.reference
        remoteConfig = Firebase.remoteConfig
        auth = Firebase.auth
    }

    private fun fetchApi(onComplete: () -> Unit) {
        setupProgress("")
        remoteConfig.fetchAndActivate().addOnCompleteListener(requireActivity()) { task ->
            if (task.isSuccessful) Global.key = remoteConfig.getString("maps_api_key")
            endProgress()
            onComplete()
        }
    }

    private fun signUp() {
        clear()
        getDetails()
        if (isCompleteDetails()) {
            setupProgress("Signing up")
            auth.createUserWithEmailAndPassword(email!!, pass!!)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val uid = task.result.user!!.uid
                        val tasks = mutableListOf<Task<Uri>>()
                        val imageUris = listOf(Pair("document", document), Pair("matrix", matrix))
                        for (uri in imageUris) {
                            val uriByte = getUriBytes(requireContext(), uri.second!!, 5_000_000)
                            val fileName = "/$uid/${uri.first}.jpg"
                            val fileRef = stRef.child(fileName)
                            val uploadTask = fileRef.putBytes(uriByte).continueWithTask { uploadTask ->
                                if (!uploadTask.isSuccessful) throw uploadTask.exception ?: Exception("Upload failed")
                                fileRef.downloadUrl
                            }
                            tasks.add(uploadTask)
                        }
                        Tasks.whenAllSuccess<Uri>(tasks)
                            .addOnSuccessListener { urls ->
                                val account = Account(uid, "Association", name, route, false)
                                val user = User(uid, phone = phone, email = email, emailVerified = false, document = "${urls.first()}", matrix = "${urls.last()}")
                                val matrix = Matrix(name, 0, 0F, 0, 0, 0F)
                                val updates = hashMapOf(
                                    "/Account/TodaGoDrive/$uid" to account,
                                    "/TodaGoDrive/Association/$uid" to user,
                                    "/Matrix/$uid" to matrix)
                                dbRef.updateChildren(updates).addOnSuccessListener {
                                    Global.account = account
                                    Global.user = user
                                    Global.deviceID = getDeviceID()
                                    dbRef.child("TodaGoDrive/${account.type}/${account.uid}/sessionID").setValue(Global.deviceID)
                                        .addOnCompleteListener { task ->
                                            if (task.isSuccessful) {
                                                endProgress()
                                                val intent = Intent(requireContext(), MainActivity::class.java)
                                                startActivity(intent)
                                                requireActivity().finish()
                                            }
                                        }
                                }
                            }
                    } else {
                        binding.loading.container.visibility = View.GONE
                        snackBar(view, "Error: ${task.exception?.message}")
                    }
                }
        } else snackBar(view, "Please fulfill all requirements")
    }

    private fun getDeviceID(): String {
        val prefs = requireContext().getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
        var deviceId = prefs.getString("device_id", null)
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit { putString("device_id", deviceId) }
        }
        return deviceId
    }

    private fun selectImage(imageType: String) {
        clear()
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED) {
            typeImage = imageType
            getImage()
        } else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun getImage() {
        when (typeImage) {
            "DOC" -> document = createImageUri(requireContext(), "document")
            else -> matrix = createImageUri(requireContext(), "matrix")
        }
        getImage.launch(when (typeImage) {
            "DOC" -> document
            else -> matrix
        })
    }

    private fun setImage() {
        when (type) {
            "DOC" -> binding.proof.text = "document.jpg"
            "MATRIX" -> binding.matrix.text = "matrix.jpg"
        }
    }

    private fun getDetails() {
        route = if (start != null) "${start!!.latitude},${start!!.longitude}${if (end != null) "-${end!!.latitude},${end!!.longitude}" else ""}" else null
        name = binding.name.text.toString()
        phone = binding.phone.text.toString()
        email = binding.email.text.toString()
        pass = binding.password.text.toString()
    }

    private fun isCompleteDetails(): Boolean = route != null && name != null &&
            binding.phone.error == null && binding.email.error == null &&
            binding.password.error == null && document != null && matrix != null

    private fun getAddress(view: TextView, type: String) {
        clear()
        this.type = type
        this.textView = view
        val intent = Intent(requireContext(), LocationPickerActivity::class.java)
        getLocation.launch(intent)
    }

    private fun showData(data: Intent?) {
        setupProgress("")
        val latitude = data?.getDoubleExtra("LATITUDE", 0.0)
        val longitude = data?.getDoubleExtra("LONGITUDE", 0.0)
        if (type.equals("START")) start = LatLng(latitude!!, longitude!!)
        else end = LatLng(latitude!!, longitude!!)
        RetrofitClient.geocodeService.reverseGeocode("$latitude,$longitude", Global.key!!)
            .enqueue(object : Callback<GeocodeResponse> {
                override fun onResponse(call: Call<GeocodeResponse?>, response: Response<GeocodeResponse?>) {
                    if (response.isSuccessful) {
                        val address = response.body()?.toLocalAddress()!!
                        textView?.text = (if (address.locality != null) "${address.locality}, " else "") +
                                (if (address.city != null) "${address.city}, " else "") +
                                (address.province ?: "")
                    } else snackBar(view, "Error: ${response.errorBody()}")
                    endProgress()
                }
                override fun onFailure(call: Call<GeocodeResponse?>, t: Throwable) {}
            })
    }

    private fun emailListener() {
        binding.email.setOnFocusChangeListener { _, focused ->
            val email = binding.email.text.toString()
            if (!focused) {
                if (email.isNotEmpty()) {
                    binding.email.error = null
                    if (!Patterns.EMAIL_ADDRESS.matcher(email).matches())
                        binding.email.error = "Invalid email address"
                } else binding.email.error = "Required"
            } else binding.email.error = "Required"
        }
    }

    private fun passwordListener() {
        binding.password.setOnFocusChangeListener { _, focused ->
            val newInput = "${binding.password.text.trim()}"
            if (!focused) {
                binding.scroller.visibility = View.GONE
                if (newInput.isNotEmpty()) {
                    binding.password.error = null
                    if (!Regex("^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[{}()\\[\\]#:;,.?!|&_~@$%=+\\-*\"']).{8,}$")
                            .matches(newInput)) binding.password.error = "Requirements not met"
                } else binding.password.error = "Required"
            } else binding.scroller.visibility = View.VISIBLE
        }
        binding.password.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (Regex("^(?=.*[A-Z]).+$").matches(s!!)) binding.uppercase.visibility = View.GONE
                else binding.uppercase.visibility = View.VISIBLE
                if (Regex("^(?=.*[a-z]).+$").matches(s)) binding.lowercase.visibility = View.GONE
                else binding.lowercase.visibility = View.VISIBLE
                if (Regex("^(?=.*[{}()\\[\\]#:;,.?!|&_~@$%=+\\-*\"']).+$").matches(s)) binding.symbol.visibility = View.GONE
                else binding.symbol.visibility = View.VISIBLE
                if (Regex("^(?=.*\\d).+$").matches(s)) binding.digit.visibility = View.GONE
                else binding.digit.visibility = View.VISIBLE
                if (Regex("^.{8,}$").matches(s)) binding.length.visibility = View.GONE
                else binding.length.visibility = View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun phoneListener() {
        binding.phone.setOnFocusChangeListener { _, focused ->
            val phoneText = binding.phone.text.toString()
            if (!focused) {
                if (phoneText.isNotEmpty()) {
                    binding.phone.error = null
                    if (!Patterns.PHONE.matcher(phoneText).matches())
                        binding.phone.error = "Invalid phone number"
                } else binding.phone.error = "Required"
            } else binding.phone.error = "Required"
        }
    }

    private fun requireCamera() {
        AlertDialog.Builder(requireContext())
            .setTitle("REQUIRED")
            .setMessage("Access to your camera is required for this feature")
            .setPositiveButton("Understood") { dialog, _ ->
                cameraPermission.launch(Manifest.permission.CAMERA)
                dialog.dismiss()
            }
            .show()
    }

    private fun setupProgress(message: String) {
        binding.loading.message.apply {
            visibility = if (message.isEmpty()) View.GONE
            else View.VISIBLE
            text = message
        }
        binding.loading.container.visibility = View.VISIBLE
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callbackFalse)
    }

    private fun endProgress() {
        binding.loading.container.visibility = View.GONE
        callbackFalse.remove()
    }

    private fun clear() {
        binding.root.clearFocus()
        hideKeyboard()
    }
}