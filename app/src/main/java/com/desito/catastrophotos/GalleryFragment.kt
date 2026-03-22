package com.desito.catastrophotos
import com.desito.catastrophotos.R

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.AttrRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.desito.catastrophotos.databinding.DialogPhotoPreviewBinding
import com.desito.catastrophotos.databinding.FragmentGalleryBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GalleryFragment : Fragment() {
    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GalleryViewModel by viewModels {
        AppViewModelFactory(MediaRepository(requireContext().applicationContext), requireContext().applicationContext)
    }

    private lateinit var folderAdapter: FolderAdapter
    private lateinit var photoAdapter: PhotoAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapters()
        setupRecyclerView()
        setupToolbar()
        setupNavigation()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.currentFolder.value == null) {
            viewModel.loadFolders()
        }
    }

    private fun setupAdapters() {
        folderAdapter = FolderAdapter(
            isSelectionMode = { viewModel.isSelectionMode.value },
            isSelected = { viewModel.selectedFolders.value.contains(it) },
            getThemeColor = ::getThemeColor,
            onClick = { folderName ->
                if (viewModel.isSelectionMode.value) {
                    viewModel.toggleFolderSelection(folderName)
                } else {
                    viewModel.openFolder(folderName)
                }
            },
            onLongClick = { folderName ->
                if (!viewModel.isSelectionMode.value) {
                    viewModel.toggleSelectionMode(folderName)
                }
            }
        )

        photoAdapter = PhotoAdapter(
            getThemeColor = ::getThemeColor,
            onClick = { uri, name -> if (uri != null) showPhotoPopup(uri, name) },
            onDelete = { uri, name -> 
                if (uri != null) confirmDelete(uri, name, viewModel.currentFolder.value ?: "") 
            }
        )
    }

    private fun setupRecyclerView() {
        binding.recyclerView.itemAnimator = null // Evitar parpadeos extra con ListAdapter
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_export -> { 
                    viewModel.exportSelected()
                    true 
                }
                R.id.action_select_all -> { 
                    viewModel.selectAll()
                    true 
                }
                R.id.action_delete -> {
                    confirmDeleteFolders()
                    true
                }
                else -> false
            }
        }
    }

    private fun confirmDeleteFolders() {
        val count = viewModel.selectedFolders.value.size
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.title_delete_folders)
            .setMessage(getString(R.string.msg_delete_folders, count))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.deleteSelectedFolders()
            }
            .show()
    }

    private fun setupNavigation() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    viewModel.isSelectionMode.value -> viewModel.toggleSelectionMode()
                    viewModel.currentFolder.value != null -> viewModel.backToFolders()
                    else -> {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.currentFolder.collectLatest { folder ->
                        updateToolbar(folder, viewModel.isSelectionMode.value, viewModel.selectedFolders.value.size)
                        if (folder == null) {
                            binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
                            binding.recyclerView.adapter = folderAdapter
                            folderAdapter.submitList(viewModel.folders.value)
                            binding.tvEmpty.visibility = if (viewModel.folders.value.isEmpty()) View.VISIBLE else View.GONE
                        } else {
                            binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
                            binding.recyclerView.adapter = photoAdapter
                            photoAdapter.submitList(viewModel.photos.value)
                            binding.tvEmpty.visibility = if (viewModel.photos.value.isEmpty()) View.VISIBLE else View.GONE
                        }
                    }
                }

                launch {
                    viewModel.folders.collectLatest { folders ->
                        if (viewModel.currentFolder.value == null) {
                            folderAdapter.submitList(folders)
                            binding.tvEmpty.visibility = if (folders.isEmpty()) View.VISIBLE else View.GONE
                        }
                    }
                }

                launch {
                    viewModel.photos.collectLatest { photos ->
                        photoAdapter.submitList(photos)
                        if (viewModel.currentFolder.value != null) {
                            binding.tvEmpty.visibility = if (photos.isEmpty()) View.VISIBLE else View.GONE
                        }
                    }
                }

                launch {
                    viewModel.isSelectionMode.collect { isSelection ->
                        updateToolbar(viewModel.currentFolder.value, isSelection, viewModel.selectedFolders.value.size)
                        folderAdapter.notifyDataSetChanged()
                    }
                }

                launch {
                    viewModel.selectedFolders.collect { selected ->
                        updateToolbar(viewModel.currentFolder.value, viewModel.isSelectionMode.value, selected.size)
                        folderAdapter.notifyDataSetChanged()
                    }
                }

                launch {
                    viewModel.exportEvent.collect { uri ->
                        shareZip(uri)
                    }
                }

                launch {
                    viewModel.errorEvent.collect { error ->
                        Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                    }
                }

                launch {
                    viewModel.isExporting.collect { isExporting ->
                        binding.loadingExport.visibility = if (isExporting) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private fun updateToolbar(folder: String?, isSelection: Boolean, selectionCount: Int) {
        binding.toolbar.menu.clear()
        when {
            isSelection -> {
                binding.toolbar.title = getString(R.string.selection_count, selectionCount)
                binding.toolbar.inflateMenu(R.menu.menu_gallery_selection)
                binding.toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel)
                binding.toolbar.setNavigationOnClickListener { viewModel.toggleSelectionMode() }
            }
            folder != null -> {
                binding.toolbar.title = folder
                binding.toolbar.setNavigationIcon(android.R.drawable.ic_menu_revert)
                binding.toolbar.setNavigationOnClickListener { viewModel.backToFolders() }
            }
            else -> {
                binding.toolbar.title = getString(R.string.title_folders)
                binding.toolbar.navigationIcon = null
                binding.toolbar.setNavigationOnClickListener(null)
            }
        }
    }

    private fun shareZip(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.action_share)))
    }

    private fun confirmDelete(uri: Uri, name: String, folderName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_delete_title)
            .setMessage(getString(R.string.dialog_delete_msg, name))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.deletePhoto(uri, folderName)
            }
            .show()
    }

    private fun showPhotoPopup(uri: Uri, name: String) {
        val dialogBinding = DialogPhotoPreviewBinding.inflate(layoutInflater)
        dialogBinding.tvPreviewName.text = name.substringBeforeLast(".")
        dialogBinding.ivPreview.load(uri) {
            listener(
                onSuccess = { _, _ -> dialogBinding.progressBar.visibility = View.GONE },
                onError = { _, _ -> dialogBinding.progressBar.visibility = View.GONE }
            )
        }
        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setBackground(ColorDrawable(Color.TRANSPARENT))
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun getThemeColor(@AttrRes attr: Int): Int {
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }
}