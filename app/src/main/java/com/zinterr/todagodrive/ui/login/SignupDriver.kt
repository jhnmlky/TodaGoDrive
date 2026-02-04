package com.zinterr.todagodrive.ui.login

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.*
import android.text.*
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Patterns
import androidx.fragment.app.Fragment
import android.view.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.*
import androidx.core.view.*
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.*
import com.google.firebase.Firebase
import com.google.firebase.auth.*
import com.google.firebase.database.*
import com.google.firebase.remoteconfig.*
import com.google.firebase.storage.*
import com.zinterr.todagodrive.network.RetrofitClient
import com.zinterr.todagodrive.R
import com.zinterr.todagodrive.MainActivity
import com.zinterr.todagodrive.TodaGoDrive
import com.zinterr.todagodrive.databinding.SignupDriverBinding
import com.zinterr.todagodrive.model.*
import com.zinterr.todagodrive.model.Global.createImageUri
import com.zinterr.todagodrive.model.Global.getAddressFromLocation
import com.zinterr.todagodrive.model.Global.getUriBytes
import com.zinterr.todagodrive.model.Global.hideKeyboard
import com.zinterr.todagodrive.model.Global.setOnDebouncedClickListener
import com.zinterr.todagodrive.model.Global.toLocalAddress
import com.zinterr.todagodrive.ui.popup.PlateDialog
import com.zinterr.todagodrive.util.*
import com.zinterr.todagodrive.viewmodel.SessionViewModel
import retrofit2.*
import java.util.UUID

@SuppressLint("ClickableViewAccessibility, SetTextI18n")
class SignupDriver : Fragment() {

    private lateinit var binding: SignupDriverBinding
    private lateinit var associations: ArrayList<Pair<String, String>>
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private lateinit var session: SessionViewModel
    private var localAddress: LocalAddress? = null
    private lateinit var dbRef: DatabaseReference
    private lateinit var stRef: StorageReference
    private var plateDialog: PlateDialog? = null
    private lateinit var auth: FirebaseAuth
    private var location: String? = null
    private var latLng: LatLng? = null
    private var fName: String? = null
    private var plate: String? = null
    private var lName: String? = null
    private var phone: String? = null
    private var assoc: String? = null
    private var email: String? = null
    private var pass: String? = null
    private var license: Uri? = null
    private var type: String? = null
    private var permit: Uri? = null
    private val scanPlate = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) verify(result.data) }
    private val getImage = registerForActivityResult(ActivityResultContracts.TakePicture()) { if (it) setImage() }
    private val getLocation = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) { if (session.key.value == null) fetchApi { showData(result.data)
        } else showData(result.data) } }
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) getImage() else requireCamera() }
    private val scanPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scan() else requireScan() }
    private val callbackFalse = object : OnBackPressedCallback(true) { override fun handleOnBackPressed() {} }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialize()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SignupDriverBinding.inflate(inflater, container, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.signupDriver) { _, insets ->
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

        addressListener()
        emailListener()
        passwordListener()
        phoneListener()
        setupAgreement()
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
            ContextCompat.getColor(requireContext(), R.color.blue_dark),
            ContextCompat.getColor(requireContext(), R.color.red))

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.locationPicker.setOnDebouncedClickListener {
            getAddress()
        }

        binding.plateScanner.setOnDebouncedClickListener {
            scanPlate()
        }

        binding.idPicker.setOnDebouncedClickListener {
            selectImage("ID")
        }

        binding.permitPicker.setOnDebouncedClickListener {
            selectImage("PERMIT")
        }

        binding.agree.setOnCheckedChangeListener { _, isChecked ->
            binding.btnSign.isEnabled = isChecked
        }

        binding.btnSign.setOnDebouncedClickListener {
            signUp()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (plateDialog != null) if (plateDialog!!.isAdded) plateDialog!!.dismiss()
    }

    private fun initialize() {
        dbRef = Firebase.database.reference
        stRef = Firebase.storage.reference
        remoteConfig = Firebase.remoteConfig
        auth = Firebase.auth
        associations = arrayListOf()
        session = ViewModelProvider((requireActivity().application as TodaGoDrive),
            ViewModelProvider.AndroidViewModelFactory
                .getInstance((requireActivity().application as TodaGoDrive)))[SessionViewModel::class.java]
    }

    private fun fetchApi(onComplete: () -> Unit) {
        setupProgress("")
        remoteConfig.fetchAndActivate().addOnCompleteListener(requireActivity()) { task ->
            if (task.isSuccessful) session.setKey(remoteConfig.getString("maps_api_key"))
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
                        val imageUris = listOf(Pair("license", license), Pair("permit", permit))
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
                                val account = Account(uid, "Driver")
                                val user = User(uid, "$fName $lName", phone, plate, location, email,
                                    assoc, 0F, 0, verified = false, emailVerified = false, license = "${urls.first()}",
                                    permit = "${urls.last()}")
                                val updates = hashMapOf(
                                    "/Account/TodaGoDrive/$uid" to account,
                                    "/TodaGoDrive/Driver/$uid" to user)
                                dbRef.updateChildren(updates).addOnSuccessListener {
                                    session.setAccount(account)
                                    session.setUser(user)
                                    session.setDeviceID(getDeviceID())
                                    dbRef.child("TodaGoDrive/${account.type}/${account.uid}/sessionID").setValue(session.deviceID.value)
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
                        endProgress()
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

    private fun scanPlate() {
        clear()
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED) scan()
        else scanPermission.launch(Manifest.permission.CAMERA)
    }

    private fun scan() {
        val intent = Intent(requireContext(), PlateScannerActivity::class.java)
        scanPlate.launch(intent)
    }

    private fun verify(data: Intent?) {
        val result = data?.getStringExtra("RESULT")
        if (result != null) {
            plateDialog = PlateDialog(result.replace('-', ' ')) { confirmed ->
                if (confirmed) binding.plate.text = result
                else scanPlate()
            }
            plateDialog!!.show(childFragmentManager, "PlateDialog")
        } else snackBar(binding.root, "Error: No valid plate number detected")
    }

    private fun selectImage(imageType: String) {
        clear()
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED) {
            type = imageType
            getImage()
        } else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun getImage() {
        when (type) {
            "ID" -> license = createImageUri(requireContext(), "license")
            else -> permit = createImageUri(requireContext(), "permit")
        }
        getImage.launch(when (type) {
            "ID" -> license
            else -> permit
        })
    }

    private fun setImage() {
        when (type) {
            "ID" -> binding.card.text = "license.jpg"
            "PERMIT" -> binding.permit.text = "permit.jpg"
        }
    }

    private fun getDetails() {
        location = if (latLng != null) "${latLng!!.latitude},${latLng!!.longitude}" else null
        fName = binding.first.text.toString()
        lName = binding.last.text.toString()
        phone = binding.phone.text.toString()
        assoc = associations.firstOrNull { it.first == binding.association.selectedItem?.toString()}?.second
        email = binding.email.text.toString()
        pass = binding.password.text.toString()
        plate = binding.plate.text.toString()
    }

    private fun isCompleteDetails(): Boolean = location != null && fName != null && lName != null &&
            binding.phone.error == null && assoc != null && binding.email.error == null &&
            binding.password.error == null && plate != null && license != null && permit != null

    private fun getAddress() {
        clear()
        val intent = Intent(requireContext(), LocationPickerActivity::class.java)
        getLocation.launch(intent)
    }

    private fun showData(data: Intent?) {
        setupProgress("")
        val latitude = data?.getDoubleExtra("LATITUDE", 0.0)
        val longitude = data?.getDoubleExtra("LONGITUDE", 0.0)
        latLng = LatLng(latitude!!, longitude!!)
        RetrofitClient.geocodeService.reverseGeocode("$latitude,$longitude", session.key.value!!)
            .enqueue(object : Callback<GeocodeResponse> {
                override fun onResponse(call: Call<GeocodeResponse?>, response: Response<GeocodeResponse?>) {
                    if (response.isSuccessful) {
                        val address = response.body()?.toLocalAddress()!!
                        localAddress = address
                        binding.location.text = (if (address.locality != null) "${address.locality}, " else "") +
                                (if (address.city != null) "${address.city}, " else "") +
                                (address.province ?: "")
                    } else snackBar(view, "Error: ${response.errorBody()}")
                    endProgress()
                }
                override fun onFailure(call: Call<GeocodeResponse?>, t: Throwable) {}
            })
    }

    private fun addressListener() {
        binding.location.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                setupAssociations()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupAssociations() {
        getAssociations {
            if (associations.isNotEmpty()) {
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item,
                    ArrayList(associations.map { it.first }))
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.association.adapter = adapter
            } else {
                binding.association.adapter = null
                snackBar(view, "No registered or verified associations in your area")
            }
        }
    }

    private fun getAssociations(onComplete: () -> Unit) {
        setupProgress("Checking associations")
        dbRef.child("Account/TodaGoDrive").orderByChild("type").equalTo("Association")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    associations.clear()
                    if (snapshot.exists()) {
                        val children = snapshot.children.toList()
                        processChildren(children, 0, localAddress, arrayListOf()) { assoc ->
                            associations.addAll(assoc)
                            endProgress()
                            onComplete()
                        }
                    } else {
                        endProgress()
                        onComplete()
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                    onComplete()
                }
            })
    }

    private fun processChildren(children: List<DataSnapshot>, index: Int = 0,
        localAddress: LocalAddress?, associations: MutableList<Pair<String, String>>,
        onComplete: (List<Pair<String, String>>) -> Unit
    ) {
        if (index >= children.size) {
            onComplete(associations)
            return
        }

        val data = children[index]
        val assoc = data.getValue(Account::class.java) ?: run {
            processChildren(children, index + 1, localAddress, associations, onComplete)
            return
        }

        val latString = assoc.route!!.substringBefore('-')
        val lat = latString.substringBefore(',').toDouble()
        val lng = latString.substringAfter(',').toDouble()
        val latLng = LatLng(lat, lng)

        getAddressFromLocation(latLng, session.key.value ?: "") { address ->
            if (address?.city == localAddress?.city && assoc.verified == true) {
                associations.add(Pair(assoc.name ?: "", assoc.uid ?: ""))
            }
            processChildren(children, index + 1, localAddress, associations, onComplete)
        }
    }

    private fun requireScan() {
        AlertDialog.Builder(requireContext())
            .setTitle("REQUIRED")
            .setMessage("Access to your camera is required for this feature")
            .setPositiveButton("Understood") { dialog, _ ->
                scanPermission.launch(Manifest.permission.CAMERA)
                dialog.dismiss()
            }
            .show()
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

    private fun setupAgreement() {
        val text = "I agree to TodaGo's Terms and Privacy Policies"
        val ss = SpannableString(text)
        val startT = text.indexOf("Terms")
        val endT = startT + "Terms".length
        val startP = text.indexOf("Privacy")
        val endP = text.length
        ss.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                findNavController().navigate(SignupDriverDirections.actionSignupDriverToTerms())
            }
            override fun updateDrawState(ds: TextPaint) {
                ds.color = ContextCompat.getColor(requireContext(), R.color.blue_dark)
                ds.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
            }
        }, startT, endT, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ss.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                findNavController().navigate(SignupDriverDirections.actionSignupDriverToPolicy())
            }
            override fun updateDrawState(ds: TextPaint) {
                ds.color = ContextCompat.getColor(requireContext(), R.color.blue_dark)
                ds.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
            }
        }, startP, endP, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.terms.text = ss
        binding.terms.movementMethod = LinkMovementMethod.getInstance()
        binding.terms.highlightColor = Color.TRANSPARENT
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