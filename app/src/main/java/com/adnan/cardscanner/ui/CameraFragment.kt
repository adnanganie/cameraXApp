package com.adnan.cardscanner.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.*
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.transaction
import com.adnan.cardscanner.R
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import com.google.firebase.ml.vision.text.FirebaseVisionText
import kotlinx.android.synthetic.main.fragment_camera.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

const val TAG = "CameraXSample"

class CameraFragment : Fragment() {

    private var imageCapture: ImageCapture? = null
    private var lensFacing = CameraX.LensFacing.BACK

    private val cameraPermissionGranted
        get() = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    private var phrase = ""


    // listener for after an image is captured
    private val imageCaptureListener = object : ImageCapture.OnImageSavedListener {
        override fun onError(error: ImageCapture.UseCaseError, message: String, exc: Throwable?) {
            Log.e(TAG, "Photo capture failed: $message", exc)
        }

        override fun onImageSaved(photoFile: File) {
            Log.d(TAG, "Photo capture succeeded: ${photoFile.absolutePath}")
            requireFragmentManager().transaction {
                replace(R.id.fragment_container, PhotoFragment.newInstance(photoFile.absolutePath))
                addToBackStack(null)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        phrase = requireArguments().getString(PHRASE_ARG, "")

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_camera, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Changes the flash mode when the button is clicked
        fab_flash.setOnClickListener {
            val flashMode = imageCapture?.flashMode
            if (flashMode == FlashMode.ON) imageCapture?.flashMode = FlashMode.OFF
            else imageCapture?.flashMode = FlashMode.ON
        }


// Changes the lens direction if the button is clicked
        fab_switch_camera.setOnClickListener {
            lensFacing = if (CameraX.LensFacing.FRONT == lensFacing) {
                CameraX.LensFacing.BACK
            } else {
                CameraX.LensFacing.FRONT
            }
            startCamera()
        }

        if (cameraPermissionGranted) {
            surfacePreview.post { startCamera() }
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), PERMISSION_CODE)
        }

        // Every time the provided texture view changes, recompute layout
        surfacePreview.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            //updateTransform()
        }
        Toast.makeText(
            requireContext(),
            "Point camera at text containing the phrase: $phrase",
            Toast.LENGTH_LONG
        )
            .show()
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE) {
            if (cameraPermissionGranted) {
                // permission granted start camera
                surfacePreview.post { startCamera() }
            } else {
                Toast.makeText(requireContext(), "Permission denied, closing.", Toast.LENGTH_LONG)
                    .show()
                requireActivity().finish()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        CameraX.unbindAll()
    }


    private fun startCamera() {
        // unbind anything that still might be open
        CameraX.unbindAll()

        val metrics = DisplayMetrics().also { surfacePreview.display.getRealMetrics(it) }
        val screenSize = Size(metrics.widthPixels, metrics.heightPixels)
        val screenAspectRatio = Rational(metrics.widthPixels, metrics.heightPixels)

        val previewConfig = PreviewConfig.Builder()
            .setLensFacing(CameraX.LensFacing.BACK)
            .setTargetAspectRatio(Rational(1, 1))
            .setTargetResolution(screenSize)
            .build()

        // Build the viewfinder use case
        val preview = AutoFitPreviewBuilder.build(previewConfig, surfacePreview)

        val analyzerConfig = ImageAnalysisConfig.Builder().apply {
            setLensFacing(CameraX.LensFacing.BACK)
            val analyzerThread = HandlerThread("OCR").apply { start() }
            setCallbackHandler(Handler(analyzerThread.looper))
            setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
            setTargetResolution(Size(1280, 720))
        }.build()

        val captureConfig = ImageCaptureConfig.Builder()
            .setLensFacing(CameraX.LensFacing.BACK)
            .setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY)
            .setTargetRotation(surfacePreview.display.rotation)
            .setTargetAspectRatio(screenAspectRatio)
            .setFlashMode(FlashMode.ON)
            .build()

        imageCapture = ImageCapture(captureConfig)

        var lastAnalyzedTimestamp = 0L
        val imageAnalysis = ImageAnalysis(analyzerConfig)
        imageAnalysis.analyzer =
            ImageAnalysis.Analyzer { image, rotationDegrees ->

                if (image?.image == null || image.image == null) return@Analyzer

                val timestamp = System.currentTimeMillis()
                // only run once per second
                if (timestamp - lastAnalyzedTimestamp >= TimeUnit.SECONDS.toMillis(1)) {
                    val visionImage = FirebaseVisionImage.fromMediaImage(
                        image.image!!,
                        getOrientationFromRotation(rotationDegrees)
                    )

                    val detector = FirebaseVision.getInstance()
                        .onDeviceTextRecognizer

                    detector.processImage(visionImage)
                        .addOnSuccessListener { result: FirebaseVisionText ->
                            // remove the new lines and join to a single string,
                            // then search for our identifier

                            val textToSearch = result.text.split("\n").joinToString(" ")
                            if (textToSearch.contains("adnan", true)) {
                                Toast.makeText(requireContext(), textToSearch, Toast.LENGTH_SHORT)
                                    .show()
                                val outputDirectory: File = requireContext().filesDir
                                val photoFile =
                                    File(outputDirectory, "${System.currentTimeMillis()}.jpg")

                                imageCapture?.takePicture(
                                    photoFile,
                                    imageCaptureListener,
                                    ImageCapture.Metadata()
                                )
                            }
                        }
                        .addOnFailureListener {
                            Log.e(TAG, "Error processing image", it)
                        }
                    lastAnalyzedTimestamp = timestamp
                }
            }

//        imageAnalysis.analyzer = TextAnalyzer(phrase){
//            Toast.makeText(requireContext(), "Text Found $it", Toast.LENGTH_SHORT).show()
//            val outputDirectory: File = requireContext().filesDir
//            val photoFile = File(outputDirectory, "${System.currentTimeMillis()}.jpg")
//
//            imageCapture?.takePicture(photoFile, imageCaptureListener, ImageCapture.Metadata())
//
//
//        }

        CameraX.bindToLifecycle(this, preview, imageAnalysis, imageCapture)
    }

    private fun getOrientationFromRotation(rotationDegrees: Int): Int {
        return when (rotationDegrees) {
            0 -> FirebaseVisionImageMetadata.ROTATION_0
            90 -> FirebaseVisionImageMetadata.ROTATION_90
            180 -> FirebaseVisionImageMetadata.ROTATION_180
            270 -> FirebaseVisionImageMetadata.ROTATION_270
            else -> FirebaseVisionImageMetadata.ROTATION_90
        }
    }

    private fun updateTransform() {
        val matrix = Matrix()
        val centerX = surfacePreview.width / 2f
        val centerY = surfacePreview.height / 2f

        val rotationDegrees = when (surfacePreview.display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }
        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)
        surfacePreview.setTransform(matrix)
    }

    companion object {

        private const val PERMISSION_CODE = 15
        private const val PHRASE_ARG = "phrase_arg"
        private const val TAG = "CameraXBasic"
        private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val PHOTO_EXTENSION = ".jpg"
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0

        /** Helper function used to create a timestamped file */
        private fun createFile(baseFolder: File, format: String, extension: String) =
            File(
                baseFolder, SimpleDateFormat(format, Locale.US)
                    .format(System.currentTimeMillis()) + extension
            )

        @JvmStatic
        fun newInstance(phrase: String): CameraFragment {
            return CameraFragment().apply {
                arguments = Bundle().apply { putString(PHRASE_ARG, phrase) }
            }
        }

    }
}

class TextAnalyzer(
    private val identifier: String,
    private val identifierDetectedCallback: (string: String) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        private val ORIENTATIONS = SparseIntArray()

        init {
            ORIENTATIONS.append(0, FirebaseVisionImageMetadata.ROTATION_0)
            ORIENTATIONS.append(90, FirebaseVisionImageMetadata.ROTATION_90)
            ORIENTATIONS.append(180, FirebaseVisionImageMetadata.ROTATION_180)
            ORIENTATIONS.append(270, FirebaseVisionImageMetadata.ROTATION_270)
        }
    }

    private var lastAnalyzedTimestamp = 0L

    private fun getOrientationFromRotation(rotationDegrees: Int): Int {
        return when (rotationDegrees) {
            0 -> FirebaseVisionImageMetadata.ROTATION_0
            90 -> FirebaseVisionImageMetadata.ROTATION_90
            180 -> FirebaseVisionImageMetadata.ROTATION_180
            270 -> FirebaseVisionImageMetadata.ROTATION_270
            else -> FirebaseVisionImageMetadata.ROTATION_90
        }
    }

    override fun analyze(image: ImageProxy?, rotationDegrees: Int) {
        if (image?.image == null || image.image == null) return

        val timestamp = System.currentTimeMillis()
        // only run once per second
        if (timestamp - lastAnalyzedTimestamp >= TimeUnit.SECONDS.toMillis(1)) {
            val visionImage = FirebaseVisionImage.fromMediaImage(
                image.image!!,
                getOrientationFromRotation(rotationDegrees)
            )

            val detector = FirebaseVision.getInstance()
                .onDeviceTextRecognizer

            detector.processImage(visionImage)
                .addOnSuccessListener { result: FirebaseVisionText ->
                    // remove the new lines and join to a single string,
                    // then search for our identifier

                    val textToSearch = result.text.split("\n").joinToString(" ")
                    if (textToSearch.contains("adnan", true)) {
                        identifierDetectedCallback(textToSearch)
                    }
                }
                .addOnFailureListener {
                    Log.e(TAG, "Error processing image", it)
                }
            lastAnalyzedTimestamp = timestamp
        }
    }
}