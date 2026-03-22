package com.desito.catastrophotos

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.TypedValue
import android.view.*
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.desito.catastrophotos.databinding.FragmentGalleryBinding
import com.desito.catastrophotos.databinding.ItemFolderBinding
import com.desito.catastrophotos.databinding.ItemPhotoBinding
import com.desito.catastrophotos.databinding.DialogPhotoPreviewBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// Clases de estado públicas para evitar errores de visibilidad en el adaptador
data class FolderUIState(val name: String, val count: Int, val isDirty: Boolean)
data class PhotoUIState(val uri: Uri?, val name: String, val isNew: Boolean, val isDeleted: Boolean)

class GalleryFragment : Fragment() {
    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!

    private var currentFolder: String? = null
    private var isSelectionMode = false
    private val selectedFolders = mutableSetOf<String>()
    private var folderStates = listOf<FolderUIState>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupNavigation()
        setupToolbarMenu()
    }

    override fun onResume() {
        super.onResume()
        refreshView()
    }

    private fun refreshView() {
        if (currentFolder == null) {
            loadFolders()
        } else {
            loadPhotos(currentFolder!!)
        }
    }

    private fun setupNavigation() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    isSelectionMode -> exitSelectionMode()
                    currentFolder != null -> {
                        currentFolder = null
                        loadFolders()
                    }
                    else -> {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    private fun setupToolbarMenu() {
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_export -> {
                    exportSelectedFolders()
                    true
                }
                R.id.action_select_all -> {
                    selectAll()
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFolders() {
        binding.toolbar.title = getString(R.string.title_folders)
        binding.toolbar.navigationIcon = null
        binding.toolbar.menu.clear()
        
        lifecycleScope.launch(Dispatchers.IO) {
            val folders = mutableMapOf<String, Int>()
            val projection = arrayOf(MediaStore.Images.Media.RELATIVE_PATH)
            val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("Pictures/CatastroPhotos/%")

            requireContext().contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs, null
            )?.use { cursor ->
                val pathIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
                while (cursor.moveToNext()) {
                    val path = cursor.getString(pathIndex)
                    val folderName = path.substringBeforeLast("/").substringAfterLast("/")
                    if (folderName.isNotEmpty() && folderName != "CatastroPhotos") {
                        folders[folderName] = folders.getOrDefault(folderName, 0) + 1
                    }
                }
            }

            folderStates = folders.toList().sortedBy { it.first }.map { (name, count) ->
                FolderUIState(name, count, isFolderDirtyBackground(name))
            }

            withContext(Dispatchers.Main) {
                if (_binding != null) {
                    binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
                    binding.recyclerView.adapter = FolderAdapter(
                        items = folderStates,
                        isSelectionMode = { isSelectionMode },
                        isSelected = { selectedFolders.contains(it) },
                        getThemeColor = ::getThemeColor,
                        onClick = { folderName ->
                            if (isSelectionMode) toggleFolderSelection(folderName)
                            else { currentFolder = folderName; loadPhotos(folderName) }
                        },
                        onLongClick = { folderName ->
                            if (!isSelectionMode) enterSelectionMode(folderName)
                        }
                    )
                    binding.tvEmpty.visibility = if (folderStates.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun isFolderDirtyBackground(folderName: String): Boolean {
        val prefs = requireContext().getSharedPreferences("FolderExportState", Context.MODE_PRIVATE)
        val savedHash = prefs.getString("hash_$folderName", null) ?: return true
        
        val (currentHash, _) = getFolderFingerprint(folderName)
        return savedHash != currentHash
    }

    private fun enterSelectionMode(folderName: String) {
        isSelectionMode = true
        selectedFolders.clear()
        selectedFolders.add(folderName)
        updateToolbarForSelection()
        binding.recyclerView.adapter?.notifyDataSetChanged()
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        selectedFolders.clear()
        binding.toolbar.menu.clear()
        binding.toolbar.title = getString(R.string.title_folders)
        binding.toolbar.navigationIcon = null
        binding.recyclerView.adapter?.notifyDataSetChanged()
    }

    private fun updateToolbarForSelection() {
        binding.toolbar.menu.clear()
        binding.toolbar.inflateMenu(R.menu.menu_gallery_selection)
        binding.toolbar.title = "${selectedFolders.size} seleccionadas"
        binding.toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel)
        binding.toolbar.setNavigationOnClickListener { exitSelectionMode() }
    }

    private fun selectAll() {
        if (selectedFolders.size == folderStates.size) {
            selectedFolders.clear()
        } else {
            selectedFolders.addAll(folderStates.map { it.name })
        }
        updateToolbarForSelection()
        binding.recyclerView.adapter?.notifyDataSetChanged()
    }

    private fun toggleFolderSelection(folderName: String) {
        if (selectedFolders.contains(folderName)) {
            selectedFolders.remove(folderName)
            if (selectedFolders.isEmpty()) exitSelectionMode()
        } else {
            selectedFolders.add(folderName)
        }
        updateToolbarForSelection()
        binding.recyclerView.adapter?.notifyDataSetChanged()
    }

    private fun exportSelectedFolders() {
        if (selectedFolders.isEmpty()) return

        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.export_loading_title)
            .setMessage(R.string.export_loading_msg)
            .setCancelable(false)
            .create()
        loadingDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Exportación y guardado de estado sincrónico
                val zipFile = createZipFromFolders(selectedFolders.toList())
                
                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.provider",
                    zipFile
                )

                withContext(Dispatchers.Main) {
                    loadingDialog.dismiss()
                    shareZip(uri)
                    exitSelectionMode()
                    // Llamamos a loadFolders para refrescar los Hash en la UI
                    loadFolders() 
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingDialog.dismiss()
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun createZipFromFolders(folders: List<String>): File {
        val dateFormat = SimpleDateFormat("HH-mm_dd-MM-yyyy", Locale.getDefault())
        val dateString = dateFormat.format(Date())

        val cacheDir = requireContext().cacheDir
        // Limpiar exports anteriores para no acumular archivos
        cacheDir.listFiles { f -> f.name.startsWith("Catastro_") && f.name.endsWith(".zip") }
            ?.forEach { it.delete() }
        val zipFile = File(cacheDir, "Catastro_$dateString.zip")

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            for (folderName in folders) {
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME
                )
                val selection = "${MediaStore.Images.Media.RELATIVE_PATH} = ?"
                val selectionArgs = arrayOf("Pictures/CatastroPhotos/$folderName/")

                requireContext().contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection, selection, selectionArgs, null
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idIdx)
                        val fileName = cursor.getString(nameIdx)
                        val uri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                        )
                        requireContext().contentResolver.openInputStream(uri)?.use { input ->
                            zos.putNextEntry(ZipEntry("$folderName/$fileName"))
                            input.copyTo(zos)
                            zos.closeEntry()
                        }
                    }
                }
                saveFolderExportStateSynchronous(folderName)
            }
        }
        return zipFile
    }

    private fun getFolderFingerprint(folderName: String): Pair<String, Set<String>> {
        val projection = arrayOf(
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE
        )
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf("Pictures/CatastroPhotos/$folderName/")
        
        val fingerprints = mutableSetOf<String>()
        val sb = StringBuilder()

        requireContext().contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, selection, selectionArgs, "${MediaStore.Images.Media.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

            while (cursor.moveToNext()) {
                val fingerprint = "${cursor.getString(nameIdx)}:${cursor.getLong(dateIdx)}:${cursor.getLong(sizeIdx)}"
                fingerprints.add(fingerprint)
                sb.append(fingerprint)
            }
        }

        val hash = try {
            MessageDigest.getInstance("MD5").digest(sb.toString().toByteArray())
                .joinToString("") { "%02x".format(it) }
        } catch (e: Exception) { "" }
        
        return hash to fingerprints
    }

    private fun saveFolderExportStateSynchronous(folderName: String) {
        val (hash, fingerprints) = getFolderFingerprint(folderName)
            
        // Usamos commit() en lugar de apply() para asegurar que el cambio sea inmediato
        // antes de que el hilo principal intente refrescar la vista.
        requireContext().getSharedPreferences("FolderExportState", Context.MODE_PRIVATE).edit().apply {
            putString("hash_$folderName", hash)
            putStringSet("files_$folderName", fingerprints)
        }.commit()
    }


    private fun shareZip(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Enviar catastro"))
    }

    private fun loadPhotos(folderName: String) {
        binding.toolbar.title = folderName
        binding.toolbar.setNavigationIcon(android.R.drawable.ic_menu_revert)
        binding.toolbar.setNavigationOnClickListener {
            currentFolder = null
            loadFolders()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val currentPhotos = mutableListOf<Triple<String, Uri, String>>() // name, uri, fingerprint
            val projection = arrayOf(
                MediaStore.Images.Media._ID, 
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.SIZE
            )
            val selection = "${MediaStore.Images.Media.RELATIVE_PATH} = ?"
            val selectionArgs = arrayOf("Pictures/CatastroPhotos/$folderName/")

            requireContext().contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs, null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx)
                    val id = cursor.getLong(idIdx)
                    val date = cursor.getLong(dateIdx)
                    val size = cursor.getLong(sizeIdx)
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    currentPhotos.add(Triple(name, uri, "$name:$date:$size"))
                }
            }

            val savedFingerprints = requireContext().getSharedPreferences("FolderExportState", Context.MODE_PRIVATE)
                .getStringSet("files_$folderName", null)

            val photoStates = mutableListOf<PhotoUIState>()
            val safeSavedFingerprints = savedFingerprints ?: emptySet()
            val isFolderNew = savedFingerprints == null

            for (photo in currentPhotos) {
                val isNew = isFolderNew || !safeSavedFingerprints.contains(photo.third)
                photoStates.add(PhotoUIState(photo.second, photo.first, isNew = isNew, isDeleted = false))
            }

            val currentNames = currentPhotos.map { it.first }.toSet()
            val savedNames = safeSavedFingerprints.map { it.substringBefore(":") }.toSet()
            
            for (oldName in savedNames) {
                if (!currentNames.contains(oldName)) {
                    photoStates.add(PhotoUIState(null, oldName, isNew = false, isDeleted = true))
                }
            }

            val sortedPhotos = photoStates.sortedBy { it.name }

            withContext(Dispatchers.Main) {
                if (_binding != null) {
                    binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
                    binding.recyclerView.adapter = PhotoAdapter(
                        items = sortedPhotos,
                        getThemeColor = ::getThemeColor,
                        onClick = { uri, name -> if (uri != null) showPhotoPopup(uri, name) },
                        onDelete = { uri, name -> if (uri != null) confirmDelete(uri, name, folderName) }
                    )
                }
            }
        }
    }

    private fun confirmDelete(uri: Uri, name: String, folderName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_delete_title)
            .setMessage(getString(R.string.dialog_delete_msg, name))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                try {
                    requireContext().contentResolver.delete(uri, null, null)
                    loadPhotos(folderName)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Error", Toast.LENGTH_SHORT).show()
                }
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