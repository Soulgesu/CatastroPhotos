package com.desito.catastrophotos
import com.desito.catastrophotos.R

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
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
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.desito.catastrophotos.databinding.FragmentCameraBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CameraFragment : Fragment() {
    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null

    private val viewModel: CameraViewModel by viewModels {
        AppViewModelFactory(MediaRepository(requireContext().applicationContext), requireContext().applicationContext)
    }

    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val permissionGranted = REQUIRED_PERMISSIONS.all { permissions[it] == true }
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
        setupInputs()
        setupControlButtons()
        observeViewModel()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            activityResultLauncher.launch(REQUIRED_PERMISSIONS)
        }

        binding.btnCapture.setOnClickListener { viewModel.onCaptureClicked() }
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()) {
            startCamera()
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    private fun setupInputs() {
        // Letra Spinner
        val letters = mutableListOf("") + ('A'..'Z').map { it.toString() }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, letters)
        binding.etLetra.setAdapter(adapter)

        // Escuchar cambios y actualizar ViewModel
        binding.etSector.doAfterTextChanged { viewModel.updateSector(it?.toString() ?: "") }
        binding.etManzana.doAfterTextChanged { viewModel.updateManzana(it?.toString() ?: "") }
        binding.etLote.doAfterTextChanged { viewModel.updateLote(it?.toString() ?: "") }
        binding.etLetra.doAfterTextChanged { viewModel.updateLetra(it?.toString() ?: "") }
    }

    private fun setupControlButtons() {
        binding.btnPlusSector.setOnClickListener { viewModel.incrementValue("sector", 1) }
        binding.btnMinusSector.setOnClickListener { viewModel.incrementValue("sector", -1) }
        binding.btnPlusManzana.setOnClickListener { viewModel.incrementValue("manzana", 1) }
        binding.btnMinusManzana.setOnClickListener { viewModel.incrementValue("manzana", -1) }
        binding.btnPlusLote.setOnClickListener { viewModel.incrementValue("lote", 1) }
        binding.btnMinusLote.setOnClickListener { viewModel.incrementValue("lote", -1) }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.sector.collectLatest { if (binding.etSector.text.toString() != it) binding.etSector.setText(it) }
        }
        lifecycleScope.launch {
            viewModel.manzana.collectLatest { if (binding.etManzana.text.toString() != it) binding.etManzana.setText(it) }
        }
        lifecycleScope.launch {
            viewModel.lote.collectLatest { if (binding.etLote.text.toString() != it) binding.etLote.setText(it) }
        }
        lifecycleScope.launch {
            viewModel.letra.collectLatest { if (binding.etLetra.text.toString() != it) binding.etLetra.setText(it, false) }
        }

        lifecycleScope.launch {
            viewModel.captureEvent.collect { action ->
                when (action) {
                    is CameraViewModel.CaptureAction.Proceed -> takePhoto(action.name, action.path)
                    is CameraViewModel.CaptureAction.ConfirmOverwrite -> {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(getString(R.string.dialog_overwrite_title))
                            .setMessage(getString(R.string.dialog_overwrite_msg, action.lote))
                            .setNegativeButton(getString(R.string.action_cancel), null)
                            .setPositiveButton(getString(R.string.action_overwrite)) { _, _ ->
                                viewModel.deleteAndProceed(action.name, action.path)
                            }
                            .show()
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.errorEvent.collect { error ->
                Snackbar.make(binding.root, error, Snackbar.LENGTH_SHORT)
                    .setAnchorView(binding.bottomCard)
                    .show()
            }
        }
    }

    private fun takePhoto(name: String, relativePath: String) {
        val imageCapture = imageCapture ?: return

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
                    val msg = when (exc.imageCaptureError) {
                        ImageCapture.ERROR_FILE_IO -> getString(R.string.error_export).replace(": %1\$s", "") // Reutilizando o simplificando
                        ImageCapture.ERROR_CAPTURE_FAILED -> "Captura fallida"
                        else -> "Error en cámara"
                    }
                    Snackbar.make(binding.root, "❌ $msg", Snackbar.LENGTH_SHORT)
                        .setAnchorView(binding.bottomCard)
                        .show()
                }
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
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
        binding.viewFinder.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    view.parent.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.parent.requestDisallowInterceptTouchEvent(false)
                }
            }

            scaleGestureDetector.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP && event.pointerCount == 1) {
                val factory = binding.viewFinder.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)
                camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
            }
            true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private val REQUIRED_PERMISSIONS = mutableListOf(Manifest.permission.CAMERA).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }
}