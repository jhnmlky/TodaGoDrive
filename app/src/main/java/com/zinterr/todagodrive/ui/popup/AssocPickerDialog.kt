package com.zinterr.todagodrive.ui.popup

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.text.*
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.*
import com.google.firebase.Firebase
import com.google.firebase.auth.*
import com.google.firebase.database.*
import com.google.firebase.storage.*
import com.zinterr.todagodrive.R
import com.zinterr.todagodrive.TodaGoDrive
import com.zinterr.todagodrive.databinding.DialogAssocPickerBinding
import com.zinterr.todagodrive.model.*
import com.zinterr.todagodrive.model.Global.createImageUri
import com.zinterr.todagodrive.model.Global.getAddressFromLocation
import com.zinterr.todagodrive.model.Global.getUriBytes
import com.zinterr.todagodrive.model.Global.hideKeyboard
import com.zinterr.todagodrive.model.Global.setOnDebouncedClickListener
import com.zinterr.todagodrive.model.Global.toLatLngString
import com.zinterr.todagodrive.model.Global.toLocalAddress
import com.zinterr.todagodrive.network.RetrofitClient
import com.zinterr.todagodrive.util.*
import com.zinterr.todagodrive.viewmodel.SessionViewModel
import retrofit2.*

@SuppressLint("SetTextI18n")
class AssocPickerDialog(
    private val apiKey: String,
    private val onComplete: (String, String, LatLng, LocalAddress) -> Unit
) : DialogFragment() {

    private lateinit var binding: DialogAssocPickerBinding
    private lateinit var associations: ArrayList<Pair<String, String>>
    private var localAddress: LocalAddress? = null
    private lateinit var session: SessionViewModel
    private lateinit var dbRef: DatabaseReference
    private lateinit var stRef: StorageReference
    private lateinit var dialog: AlertDialog
    private lateinit var auth: FirebaseAuth
    private var assocName: String? = null
    private var assocUID: String? = null
    private var latLng: LatLng? = null
    private var type: String? = null
    private var license: Uri? = null
    private var permit: Uri? = null
    private var uid: String? = null
    private val getImage = registerForActivityResult(ActivityResultContracts.TakePicture()) { if (it) setImage() }
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) getImage() else requireCamera() }
    private val getLocation = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) showData(result.data) }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogAssocPickerBinding.inflate(layoutInflater)
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(binding.root)
        dialog = builder.create()
        dialog.setCanceledOnTouchOutside(false)

        dbRef = Firebase.database.reference
        stRef = Firebase.storage.reference
        auth = Firebase.auth
        associations = arrayListOf()
        uid = auth.currentUser!!.uid
        session = ViewModelProvider((requireActivity().application as TodaGoDrive),
            ViewModelProvider.AndroidViewModelFactory
                .getInstance((requireActivity().application as TodaGoDrive)))[SessionViewModel::class.java]

        addressListener()

        binding.loading.progress.setIndicatorColor(
            ContextCompat.getColor(requireContext(), R.color.blue_dark),
            ContextCompat.getColor(requireContext(), R.color.red))

        binding.locationPicker.setOnDebouncedClickListener {
            getAddress()
        }

        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        binding.idPicker.setOnDebouncedClickListener {
            selectImage("ID")
        }

        binding.permitPicker.setOnDebouncedClickListener {
            selectImage("PERMIT")
        }

        binding.btnChange.setOnDebouncedClickListener {
            changeAssoc()
        }

        if (dialog.window != null) dialog.window!!.setBackgroundDrawable(0.toDrawable())
        return dialog
    }

    private fun changeAssoc() {
        if (isCompleteDetails()) {
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
                    val updates = hashMapOf<String, Any>(
                        "/verified" to false,
                        "/location" to latLng!!.toLatLngString(),
                        "/assoc" to assocUID!!,
                        "/license" to urls.first(),
                        "/permit" to urls.last())
                    dbRef.child("TodaGoDrive/Driver/$uid").updateChildren(updates)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                onComplete(assocUID!!, assocName!!, latLng!!, localAddress!!)
                                dialog.dismiss()
                            } else snackBar(binding.root, "Error: ${task.exception!!.message}")
                            binding.loading.container.visibility = View.GONE
                        }
                }
        } else snackBar(binding.root, "Please fulfill all requirements")
    }

    private fun isCompleteDetails(): Boolean {
        assocUID = associations.firstOrNull { it.first == binding.association.selectedItem?.toString()}?.second
        assocName = associations.firstOrNull { it.first == binding.association.selectedItem?.toString()}?.first
        return assocUID != null && assocName != null && latLng != null && localAddress != null && license != null && permit != null
    }

    private fun getAddress() {
        val intent = Intent(requireContext(), LocationPickerActivity::class.java)
        getLocation.launch(intent)
    }

    private fun showData(data: Intent?) {
        setupProgress("")
        val latitude = data?.getDoubleExtra("LATITUDE", 0.0)
        val longitude = data?.getDoubleExtra("LONGITUDE", 0.0)
        latLng = LatLng(latitude!!, longitude!!)
        RetrofitClient.geocodeService.reverseGeocode("$latitude,$longitude", apiKey)
            .enqueue(object : Callback<GeocodeResponse> {
                override fun onResponse(call: Call<GeocodeResponse?>, response: Response<GeocodeResponse?>) {
                    if (response.isSuccessful) {
                        val address = response.body()?.toLocalAddress()!!
                        localAddress = address
                        binding.location.text = (if (address.locality != null) "${address.locality}, " else "") +
                                (if (address.city != null) "${address.city}, " else "") +
                                (address.province ?: "")
                    } else snackBar(binding.root, "Error: ${response.errorBody()}")
                    binding.loading.container.visibility = View.GONE
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
                snackBar(binding.root, "No registered associations in your area")
            }
        }
    }

    private fun getAssociations(onComplete: () -> Unit) {
        setupProgress("Checking associations")
        dbRef.child("Account/TodaGoDrive").orderByChild("type").equalTo("Association")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        associations.clear()
                        for (data in snapshot.children) {
                            val assoc = data.getValue(Account::class.java)!!
                            val latLng = assoc.route!!.substringBefore('-')
                            getAddressFromLocation(LatLng(latLng.substringBefore(',').toDouble(),
                                latLng.substringAfter(',').toDouble()), session.key.value!!
                            ) { address ->
                                if (address?.city.equals(localAddress?.city) && assoc.verified == true)
                                    associations.add(Pair("${assoc.name}", "${assoc.uid}"))
                            }
                        }
                    }
                    binding.loading.container.visibility = View.GONE
                    Handler(Looper.getMainLooper()).postDelayed({
                        onComplete()
                    }, 100)
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(binding.root, "Error: ${error.message}")
                    onComplete()
                }
            })
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
    }

    private fun clear() {
        binding.root.clearFocus()
        hideKeyboard()
    }
}