package com.zinterr.todagodrive.util

import android.content.Intent
import android.os.*
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.*
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.zinterr.todagodrive.databinding.ActivityPlateScannerBinding
import com.zinterr.todagodrive.model.Global.setOnDebouncedClickListener

class PlateScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlateScannerBinding
    private var cameraProvider: ProcessCameraProvider? = null
    private var doScan: Boolean? = null
    private var height: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPlateScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.plateScanner) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val params = binding.btnScan.layoutParams as ViewGroup.MarginLayoutParams
            Handler(Looper.getMainLooper()).postDelayed({
                if (height == null) height = v.height
                height?.let { params.bottomMargin = ((it * 0.1) + navBars.bottom).toInt() }
                binding.btnScan.layoutParams = params
            }, 0)
            insets
        }
        startCamera()

        binding.btnScan.setOnDebouncedClickListener {
            doScan = true
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            cameraProvider = provider
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.previewView.surfaceProvider
            }
            val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                        if (doScan == true) {
                            doScan = false
                            processImageProxy(textRecognizer, imageProxy)
                        } else imageProxy.close()
                    }
                }
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer)
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(recognizer: TextRecognizer, imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: return
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val detectedText = visionText.text
                val normal = detectedText.uppercase()
                val tokenRegex = Regex("(?<![A-Z0-9- ])[A-Z0-9- ]{6,12}(?![A-Z0-9- ])")
                val plateCandidate = tokenRegex.findAll(normal).map { it.value.trim() }
                    .map { it.replace("[\\s-]+".toRegex(), "-") }.firstOrNull { candidate ->
                        candidate.any { it.isDigit() } && candidate.any { it in 'A'..'Z' }
                    }
                plateCandidate?.let { plate ->
                    val intent = Intent().apply {
                        putExtra("RESULT", plate)
                    }
                    setResult(RESULT_OK, intent)
                    finish()
                } ?: snackBar(binding.root, "No valid plate number")
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}