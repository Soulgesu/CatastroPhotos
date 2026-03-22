package com.desito.catastrophotos

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CameraViewModel(private val repository: MediaRepository, context: Context) : ViewModel() {

    private val prefs = context.getSharedPreferences("CatastroPrefs", Context.MODE_PRIVATE)

    private val _sector = MutableStateFlow(prefs.getString("sector", "01") ?: "01")
    val sector: StateFlow<String> = _sector.asStateFlow()

    private val _manzana = MutableStateFlow(prefs.getString("manzana", "001") ?: "001")
    val manzana: StateFlow<String> = _manzana.asStateFlow()

    private val _lote = MutableStateFlow(prefs.getString("lote", "001") ?: "001")
    val lote: StateFlow<String> = _lote.asStateFlow()

    private val _letra = MutableStateFlow(prefs.getString("letra", "") ?: "")
    val letra: StateFlow<String> = _letra.asStateFlow()

    private val _captureEvent = MutableSharedFlow<CaptureAction>()
    val captureEvent: SharedFlow<CaptureAction> = _captureEvent.asSharedFlow()

    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent: SharedFlow<String> = _errorEvent.asSharedFlow()

    sealed class CaptureAction {
        data class Proceed(val name: String, val path: String) : CaptureAction()
        data class ConfirmOverwrite(val name: String, val path: String, val lote: String) : CaptureAction()
    }

    fun updateSector(value: String) { _sector.value = value.padStart(2, '0'); save() }
    fun updateManzana(value: String) { _manzana.value = value.padStart(3, '0'); save() }
    fun updateLote(value: String) { _lote.value = value.padStart(3, '0'); save() }
    fun updateLetra(value: String) { _letra.value = value; save() }

    fun incrementValue(type: String, delta: Int) {
        when (type) {
            "sector" -> updateSector(((_sector.value.toIntOrNull() ?: 0) + delta).coerceAtLeast(1).toString())
            "manzana" -> updateManzana(((_manzana.value.toIntOrNull() ?: 0) + delta).coerceAtLeast(1).toString())
            "lote" -> updateLote(((_lote.value.toIntOrNull() ?: 0) + delta).coerceAtLeast(1).toString())
        }
    }

    private fun save() {
        prefs.edit().apply {
            putString("sector", _sector.value)
            putString("manzana", _manzana.value)
            putString("lote", _lote.value)
            putString("letra", _letra.value)
            apply()
        }
    }

    fun onCaptureClicked() {
        val s = _sector.value
        val m = _manzana.value
        val l = _lote.value
        val let = _letra.value

        if (s.isEmpty() || m.isEmpty() || l.isEmpty()) {
            viewModelScope.launch { _errorEvent.emit("Completa Sector, Manzana y Lote") }
            return
        }

        val folderName = "${s}_${m}"
        val fileName = if (let.isEmpty()) "${s}_${m}_${l}" else "${s}_${m}_${l}_${let}"
        val relativePath = "Pictures/CatastroPhotos/$folderName"

        viewModelScope.launch {
            if (repository.photoExists(fileName, relativePath)) {
                _captureEvent.emit(CaptureAction.ConfirmOverwrite(fileName, relativePath, l))
            } else {
                _captureEvent.emit(CaptureAction.Proceed(fileName, relativePath))
            }
        }
    }

    fun deleteAndProceed(name: String, path: String) {
        viewModelScope.launch {
            repository.deletePhotoByName(name, path)
            _captureEvent.emit(CaptureAction.Proceed(name, path))
        }
    }

    fun clearCache() {
        repository.clearCache()
    }
}
