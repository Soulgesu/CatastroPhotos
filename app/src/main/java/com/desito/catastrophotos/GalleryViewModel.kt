package com.desito.catastrophotos

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GalleryViewModel(private val repository: MediaRepository) : ViewModel() {

    private val _folders = MutableStateFlow<List<FolderUIState>>(emptyList())
    val folders: StateFlow<List<FolderUIState>> = _folders.asStateFlow()

    private val _photos = MutableStateFlow<List<PhotoUIState>>(emptyList())
    val photos: StateFlow<List<PhotoUIState>> = _photos.asStateFlow()

    private val _currentFolder = MutableStateFlow<String?>(null)
    val currentFolder: StateFlow<String?> = _currentFolder.asStateFlow()

    private val _selectedFolders = MutableStateFlow<Set<String>>(emptySet())
    val selectedFolders: StateFlow<Set<String>> = _selectedFolders.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _exportEvent = MutableSharedFlow<Uri>()
    val exportEvent: SharedFlow<Uri> = _exportEvent.asSharedFlow()

    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent: SharedFlow<String> = _errorEvent.asSharedFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    fun loadFolders() {
        viewModelScope.launch {
            _folders.value = repository.getFolders()
        }
    }

    fun openFolder(folderName: String) {
        _currentFolder.value = folderName
        _photos.value = emptyList()
        viewModelScope.launch {
            _photos.value = repository.getPhotos(folderName)
        }
    }

    fun backToFolders() {
        _currentFolder.value = null
        _photos.value = emptyList()
        loadFolders()
    }

    fun toggleSelectionMode(initialFolder: String? = null) {
        _isSelectionMode.value = !_isSelectionMode.value
        if (_isSelectionMode.value) {
            _selectedFolders.value = initialFolder?.let { setOf(it) } ?: emptySet()
        } else {
            _selectedFolders.value = emptySet()
        }
    }

    fun toggleFolderSelection(folderName: String) {
        val currentSelected = _selectedFolders.value.toMutableSet()
        if (currentSelected.contains(folderName)) {
            currentSelected.remove(folderName)
            if (currentSelected.isEmpty()) {
                _isSelectionMode.value = false
            }
        } else {
            currentSelected.add(folderName)
        }
        _selectedFolders.value = currentSelected
    }

    fun selectAll() {
        if (_selectedFolders.value.size == _folders.value.size) {
            _selectedFolders.value = emptySet()
        } else {
            _selectedFolders.value = _folders.value.map { it.name }.toSet()
        }
    }

    fun exportSelected() {
        val foldersToExport = _selectedFolders.value.toList()
        if (foldersToExport.isEmpty()) return

        _isExporting.value = true
        viewModelScope.launch {
            try {
                val uri = repository.exportFolders(foldersToExport)
                _exportEvent.emit(uri)
                _isSelectionMode.value = false
                _selectedFolders.value = emptySet()
                loadFolders() // Refrescar estado "dirty"
            } catch (e: Exception) {
                _errorEvent.emit("Error al exportar: ${e.message}")
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun deletePhoto(uri: Uri, folderName: String) {
        viewModelScope.launch {
            if (repository.deletePhoto(uri)) {
                _photos.value = repository.getPhotos(folderName)
            } else {
                _errorEvent.emit("No se pudo eliminar la foto")
            }
        }
    }

    fun deleteSelectedFolders() {
        val foldersToDelete = _selectedFolders.value.toList()
        if (foldersToDelete.isEmpty()) return

        viewModelScope.launch {
            if (repository.deleteFolders(foldersToDelete)) {
                _isSelectionMode.value = false
                _selectedFolders.value = emptySet()
                loadFolders()
            } else {
                _errorEvent.emit("No se pudieron eliminar algunas carpetas")
            }
        }
    }
}
