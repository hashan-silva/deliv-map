package com.hashan0314.delivmap

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var scanButton: Button
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required to scan labels.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        scanButton = findViewById(R.id.scanButton)
        cameraExecutor = Executors.newSingleThreadExecutor()

        scanButton.setOnClickListener {
            if (hasCameraPermission()) {
                captureAndProcessFrame()
            } else {
                requestCameraPermission()
            }
        }

        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestCameraPermission()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                this.imageCapture = imageCapture
            } catch (ex: Exception) {
                Toast.makeText(this, "Unable to start camera: ${ex.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureAndProcessFrame() {
        val imageCapture = imageCapture ?: run {
            Toast.makeText(this, "Camera not ready yet.", Toast.LENGTH_SHORT).show()
            return
        }

        scanButton.isEnabled = false
        scanButton.text = getString(R.string.scan_button) + "…"

        imageCapture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                processImage(image)
            }

            override fun onError(exception: ImageCaptureException) {
                runOnUiThread {
                    scanButton.isEnabled = true
                    scanButton.text = getString(R.string.scan_button)
                    Toast.makeText(this@MainActivity, "Failed to capture image: ${exception.message}", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    private fun processImage(imageProxy: androidx.camera.core.ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            runOnUiThread {
                scanButton.isEnabled = true
                scanButton.text = getString(R.string.scan_button)
                Toast.makeText(this, "Unable to capture frame.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val candidates = AddressHeuristics.extractCandidates(visionText.text)
                val intent = Intent(this, MapActivity::class.java).apply {
                    putStringArrayListExtra(
                        MapActivity.EXTRA_ADDRESS_CANDIDATES,
                        ArrayList(candidates)
                    )
                }
                if (candidates.isEmpty()) {
                    Toast.makeText(
                        this,
                        getString(R.string.no_candidates_message),
                        Toast.LENGTH_LONG
                    ).show()
                }
                startActivity(intent)
            }
            .addOnFailureListener { error ->
                Toast.makeText(
                    this,
                    "Text recognition failed: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
            .addOnCompleteListener {
                imageProxy.close()
                recognizer.close()
                scanButton.isEnabled = true
                scanButton.text = getString(R.string.scan_button)
            }
    }
}
