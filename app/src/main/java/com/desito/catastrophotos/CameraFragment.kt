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
import androidx.camera.core.ImageCapture.OutputFileOptions
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

    private var currentRatioIndex = 0
    private val ratios = listOf("4:3", "1:1", "Full")
    private var orientationEventListener: android.view.OrientationEventListener? = null

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

        orientationEventListener = object : android.view.OrientationEventListener(requireContext()) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val rotation = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270 // Reverse Landscape
                    in 135..224 -> Surface.ROTATION_180 // Reverse Portrait
                    in 225..314 -> Surface.ROTATION_90 // Landscape
                    else -> Surface.ROTATION_0 // Portrait
                }
                imageCapture?.targetRotation = rotation
            }
        }

        binding.btnAspectRatio.setOnClickListener {
            currentRatioIndex = (currentRatioIndex + 1) % ratios.size
            binding.btnAspectRatio.text = ratios[currentRatioIndex]
            updateLayoutForAspectRatio()
            if (allPermissionsGranted()) {
                startCamera()
            }
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            activityResultLauncher.launch(REQUIRED_PERMISSIONS)
        }

        binding.btnCapture.setOnClickListener { viewModel.onCaptureClicked() }
    }

    override fun onStart() {
        super.onStart()
        orientationEventListener?.enable()
    }

    override fun onStop() {
        super.onStop()
        orientationEventListener?.disable()
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
                    is CameraViewModel.CaptureAction.Proceed -> {
                        showFlashAnimation()
                        takePhoto(action.name, action.path)
                    }
                    is CameraViewModel.CaptureAction.ConfirmOverwrite -> {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(getString(R.string.dialog_overwrite_title))
                            .setMessage(getString(R.string.dialog_overwrite_msg, action.lote))
                            .setNegativeButton(getString(R.string.action_cancel), null)
                            .setPositiveButton(getString(R.string.action_overwrite)) { _, _ ->
                                showFlashAnimation()
                                viewModel.deleteAndProceed(action.name, action.path)
                            }
                            .show()
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.errorEvent.collect { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showFlashAnimation() {
        binding.flashOverlay.alpha = 1f
        binding.flashOverlay.visibility = View.VISIBLE
        binding.flashOverlay.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction { binding.flashOverlay.visibility = View.GONE }
            .start()
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
                        ImageCapture.ERROR_FILE_IO -> getString(R.string.error_export).replace(": %1\$s", "")
                        ImageCapture.ERROR_CAPTURE_FAILED -> "Captura fallida"
                        else -> "Error en cámara"
                    }
                    Toast.makeText(requireContext(), "❌ $msg", Toast.LENGTH_SHORT).show()
                }
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val currentFolder = "${viewModel.sector.value}_${viewModel.manzana.value}"
                    viewModel.notifyPhotoSaved(currentFolder)
                    Toast.makeText(requireContext(), "📸 $name guardada", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun updateLayoutForAspectRatio() {
        val layoutParams = binding.cameraCard.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        when (currentRatioIndex) {
            0 -> layoutParams.dimensionRatio = "3:4" // 4:3 en vertical
            1 -> layoutParams.dimensionRatio = "1:1" // Cuadrado
            2 -> layoutParams.dimensionRatio = null  // Full (llena el espacio)
        }
        binding.cameraCard.layoutParams = layoutParams
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            
            // Decidir la estrategia de resolución de CameraX
            val aspectRatioStrategy = if (currentRatioIndex == 2) {
                androidx.camera.core.resolutionselector.AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
            } else {
                androidx.camera.core.resolutionselector.AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
            }

            val resolutionSelector = androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
                .setAspectRatioStrategy(aspectRatioStrategy)
                .build()

            val preview = Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setResolutionSelector(resolutionSelector)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
                
            val viewPort = ViewPort.Builder(
                when (currentRatioIndex) {
                    0 -> android.util.Rational(3, 4)
                    1 -> android.util.Rational(1, 1)
                    else -> android.util.Rational(9, 16) // Ajustable a FullScreen
                },
                binding.viewFinder.display?.rotation ?: Surface.ROTATION_0
            ).build()

            val useCaseGroup = UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(imageCapture!!)
                .setViewPort(viewPort)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(viewLifecycleOwner, cameraSelector, useCaseGroup)
                setupZoomAndFocus()
            } catch(exc: Exception) {
                Log.e("CameraFragment", "Error camera", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupZoomAndFocus() {
        val scaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentZoomRatio = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
                camera?.cameraControl?.setZoomRatio(currentZoomRatio * detector.scaleFactor)
                return true
            }
        }
        val scaleGestureDetector = ScaleGestureDetector(requireContext(), scaleListener)

        val tapDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val factory = binding.viewFinder.meteringPointFactory
                val point = factory.createPoint(e.x, e.y)
                camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                animateFocusRing(e.x, e.y)
                return true
            }
        })

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
            tapDetector.onTouchEvent(event)
            true
        }
    }

    private fun animateFocusRing(x: Float, y: Float) {
        val ring = binding.viewFocusRing
        ring.x = x - ring.width / 2
        ring.y = y - ring.height / 2
        ring.visibility = View.VISIBLE
        ring.alpha = 1f
        ring.scaleX = 1.5f
        ring.scaleY = 1.5f

        ring.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(0f)
            .setDuration(300)
            .withEndAction { ring.visibility = View.INVISIBLE }
            .start()
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