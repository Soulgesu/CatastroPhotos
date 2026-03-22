package com.desito.catastrophotos

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MediaRepository(private val context: Context) {

    private val contentResolver = context.contentResolver
    private val folderPrefs = context.getSharedPreferences("FolderExportState", Context.MODE_PRIVATE)

    // Caché en memoria para fluidez
    private var cachedFolders: List<FolderUIState>? = null
    private val cachedPhotos = mutableMapOf<String, List<PhotoUIState>>()

    fun clearCache() {
        cachedFolders = null
        cachedPhotos.clear()
    }

    suspend fun getFolders(): List<FolderUIState> = withContext(Dispatchers.IO) {
        cachedFolders?.let { return@withContext it }
        val folders = mutableMapOf<String, Int>()
        val projection = arrayOf(MediaStore.Images.Media.RELATIVE_PATH)
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("Pictures/CatastroPhotos/%")

        contentResolver.query(
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

        val result = folders.toList().sortedBy { it.first }.map { (name, count) ->
            FolderUIState(name, count, isFolderDirty(name))
        }
        cachedFolders = result
        result
    }

    suspend fun getPhotos(folderName: String): List<PhotoUIState> = withContext(Dispatchers.IO) {
        cachedPhotos[folderName]?.let { return@withContext it }
        val currentPhotos = mutableListOf<Triple<String, Uri, String>>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE
        )
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf("Pictures/CatastroPhotos/$folderName/")

        contentResolver.query(
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

        val savedFingerprints = folderPrefs.getStringSet("files_$folderName", null)
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

        val result = photoStates.sortedBy { it.name }
        cachedPhotos[folderName] = result
        result
    }

    suspend fun photoExists(fileName: String, relativePath: String): Boolean = withContext(Dispatchers.IO) {
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf("$fileName.jpg", "$relativePath/")

        contentResolver.query(uri, projection, selection, selectionArgs, null)?.use {
            it.count > 0
        } ?: false
    }

    suspend fun deletePhotoByName(fileName: String, relativePath: String) = withContext(Dispatchers.IO) {
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf("$fileName.jpg", "$relativePath/")
        contentResolver.delete(uri, selection, selectionArgs)
        clearCache()
    }

    private fun isFolderDirty(folderName: String): Boolean {
        val savedHash = folderPrefs.getString("hash_$folderName", null) ?: return true
        val (currentHash, _) = getFolderFingerprint(folderName)
        return savedHash != currentHash
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

        contentResolver.query(
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

    suspend fun exportFolders(folders: List<String>): Uri = withContext(Dispatchers.IO) {
        val dateFormat = SimpleDateFormat("HH-mm_dd-MM-yyyy", Locale.getDefault())
        val dateString = dateFormat.format(Date())

        val cacheDir = context.cacheDir
        cacheDir.listFiles { f -> f.name.startsWith("Catastro_") && f.name.endsWith(".zip") }
            ?.forEach { it.delete() }

        val zipFile = File(cacheDir, "Catastro_$dateString.zip")

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            for (folderName in folders) {
                val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
                val selection = "${MediaStore.Images.Media.RELATIVE_PATH} = ?"
                val selectionArgs = arrayOf("Pictures/CatastroPhotos/$folderName/")

                contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection, selection, selectionArgs, null
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idIdx)
                        val fileName = cursor.getString(nameIdx)
                        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        contentResolver.openInputStream(uri)?.use { input ->
                            zos.putNextEntry(ZipEntry("$folderName/$fileName"))
                            input.copyTo(zos)
                            zos.closeEntry()
                        }
                    }
                }
                saveFolderExportState(folderName)
            }
        }

        FileProvider.getUriForFile(context, "${context.packageName}.provider", zipFile)
    }

    private fun saveFolderExportState(folderName: String) {
        val (hash, fingerprints) = getFolderFingerprint(folderName)
        folderPrefs.edit().apply {
            putString("hash_$folderName", hash)
            putStringSet("files_$folderName", fingerprints)
            commit() // Usamos commit para asegurar persistencia inmediata
        }
    }

    suspend fun deletePhoto(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = contentResolver.delete(uri, null, null) > 0
            if (result) clearCache()
            result
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteFolders(folders: List<String>): Boolean = withContext(Dispatchers.IO) {
        try {
            for (folderName in folders) {
                val selection = "${MediaStore.Images.Media.RELATIVE_PATH} = ?"
                val selectionArgs = arrayOf("Pictures/CatastroPhotos/$folderName/")
                contentResolver.delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection, selectionArgs)
                // Limpiar metadatos de la carpeta
                folderPrefs.edit().apply {
                    remove("hash_$folderName")
                    remove("files_$folderName")
                    apply()
                }
            }
            clearCache()
            true
        } catch (e: Exception) {
            false
        }
    }
}
