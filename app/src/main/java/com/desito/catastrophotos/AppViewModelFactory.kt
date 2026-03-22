package com.desito.catastrophotos

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class AppViewModelFactory(private val repository: MediaRepository, private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(GalleryViewModel::class.java) -> GalleryViewModel(repository) as T
            modelClass.isAssignableFrom(CameraViewModel::class.java) -> CameraViewModel(repository, context) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
