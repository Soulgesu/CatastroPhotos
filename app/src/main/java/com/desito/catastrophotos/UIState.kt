package com.desito.catastrophotos

import android.net.Uri

data class FolderUIState(
    val name: String,
    val count: Int,
    val isDirty: Boolean
)

data class PhotoUIState(
    val uri: Uri?,
    val name: String,
    val isNew: Boolean,
    val isDeleted: Boolean
)
