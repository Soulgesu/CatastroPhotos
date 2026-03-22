package com.desito.catastrophotos

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.*
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.desito.catastrophotos.databinding.FragmentCameraBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment : Fragment() {
    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var sharedPreferences: SharedPreferences

    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value) permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(requireContext(), "Permisos no concedidos", Toast.LENGTH_SHORT).show()
            } else {
                startCamera()
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedPreferences = requireContext().getSharedPreferences("CatastroPrefs", Context.MODE_PRIVATE)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setupLetterSpinner()
        loadSavedValues()
        setupControlButtons()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        binding.btnCapture.setOnClickListener { checkAndTakePhoto() }
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    private fun setupLetterSpinner() {
        val letters = mutableListOf<String>("")
        for (i in 'A'..'Z') {
            letters.add(i.toString())
        }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, letters)
        binding.etLetra.setAdapter(adapter)
        binding.etLetra.setText("", false)
    }

    private fun setupControlButtons() {
        binding.btnPlusSector.setOnClickListener { updateValue("sector", 1) }
        binding.btnMinusSector.setOnClickListener { updateValue("sector", -1) }
        binding.btnPlusManzana.setOnClickListener { updateValue("manzana", 1) }
        binding.btnMinusManzana.setOnClickListener { updateValue("manzana", -1) }
        binding.btnPlusLote.setOnClickListener { updateValue("lote", 1) }
        binding.btnMinusLote.setOnClickListener { updateValue("lote", -1) }
    }

    private fun updateValue(type: String, delta: Int) {
        val editText = when(type) {
            "sector" -> binding.etSector
            "manzana" -> binding.etManzana
            "lote" -> binding.etLote
            else -> return
        }
        val length = if (type == "sector") 2 else 3
        val currentStr = editText.text.toString().trim()
        val currentValue = if (currentStr.isEmpty()) 0 else currentStr.toIntOrNull() ?: 0
        val newValue = (currentValue + delta).coerceAtLeast(1)
        editText.setText(newValue.toString().padStart(length, '0'))
    }

    private fun loadSavedValues() {
        binding.etSector.setText(sharedPreferences.getString("sector", "01"))
        binding.etManzana.setText(sharedPreferences.getString("manzana", "001"))
        binding.etLote.setText(sharedPreferences.getString("lote", "001"))
    }

    private fun saveValues(sector: String, manzana: String, lote: String, letra: String) {
        sharedPreferences.edit().apply {
            putString("sector", sector)
            putString("manzana", manzana)
            putString("lote", lote)
            putString("letra", letra)
            apply()
        }
    }

    private fun checkAndTakePhoto() {
        val sectorInput = binding.etSector.text.toString().trim()
        val manzanaInput = binding.etManzana.text.toString().trim()
        val loteInput = binding.etLote.text.toString().trim()
        val letra = binding.etLetra.text.toString().trim()

        if (sectorInput.isEmpty() || manzanaInput.isEmpty() || loteInput.isEmpty()) {
            Snackbar.make(binding.root, getString(R.string.msg_complete_fields), Snackbar.LENGTH_SHORT)
                .setAnchorView(binding.bottomCard)
                .show()
            return
        }

        val sector = sectorInput.padStart(2, '0')
        val manzana = manzanaInput.padStart(3, '0')
        val lote = loteInput.padStart(3, '0')
        val folderName = "${sector}_${manzana}"
        val fileName = if (letra.isEmpty()) "${sector}_${manzana}_${lote}" else "${sector}_${manzana}_${lote}_${letra}"
        val relativePath = "Pictures/CatastroPhotos/$folderName"

        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf("$fileName.jpg", "$relativePath/")

        val cursor = requireContext().contentResolver.query(uri, projection, selection, selectionArgs, null)
        val exists = cursor?.use { it.count > 0 } ?: false

        if (exists) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.dialog_overwrite_title))
                .setMessage(getString(R.string.dialog_overwrite_msg, lote))
                .setNegativeButton(getString(R.string.action_cancel), null)
                .setPositiveButton(getString(R.string.action_overwrite)) { _, _ ->
                    requireContext().contentResolver.delete(uri, selection, selectionArgs)
                    takePhoto(fileName, relativePath)
                }
                .show()
        } else {
            takePhoto(fileName, relativePath)
        }
    }

    private fun takePhoto(name: String, relativePath: String) {
        val imageCapture = imageCapture ?: return

        saveValues(
            binding.etSector.text.toString().trim().padStart(2, '0'),
            binding.etManzana.text.toString().trim().padStart(3, '0'),
            binding.etLote.text.toString().trim().padStart(3, '0'),
            binding.etLetra.text.toString().trim()
        )

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            requireContext().contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Snackbar.make(binding.root, "❌ Error", Snackbar.LENGTH_SHORT)
                        .setAnchorView(binding.bottomCard)
                        .show()
                }
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    sharedPreferences.edit().putString("last_photo_uri", output.savedUri.toString()).apply()
                    Snackbar.make(binding.root, "📸 $name", Snackbar.LENGTH_SHORT)
                        .setAnchorView(binding.bottomCard)
                        .show()
                }
            }
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build()
                .also { it.setSurfaceProvider(binding.viewFinder.surfaceProvider) }
            imageCapture = ImageCapture.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(viewLifecycleOwner, cameraSelector, preview, imageCapture)
                setupZoomAndFocus()
            } catch(exc: Exception) {
                Log.e("CameraFragment", "Error camera", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupZoomAndFocus() {
        val listener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentZoomRatio = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
                camera?.cameraControl?.setZoomRatio(currentZoomRatio * detector.scaleFactor)
                return true
            }
        }
        val scaleGestureDetector = ScaleGestureDetector(requireContext(), listener)
        binding.viewFinder.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP) {
                val factory = binding.viewFinder.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)
                camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
            }
            true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }

    companion object {
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
}