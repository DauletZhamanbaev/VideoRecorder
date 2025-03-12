package com.dashcam.videorecorder.gallery


import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dashcam.videorecorder.gallery.GalleryItem
import com.dashcam.videorecorder.gallery.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

enum class MediaType {
    IMAGE, VIDEO
}

data class GalleryItem(
    val file: File,
    val mediaType: MediaType
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val _imageItems = MutableStateFlow<List<GalleryItem>>(emptyList())
    val imageItems: StateFlow<List<GalleryItem>> = _imageItems.asStateFlow()

    private val _videoItems = MutableStateFlow<List<GalleryItem>>(emptyList())
    val videoItems: StateFlow<List<GalleryItem>> = _videoItems.asStateFlow()

    init {
        loadGalleryItems()
    }

    private fun loadGalleryItems() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            // Каталоги для фото и видео
            val imagesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val videosDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            val imageFiles = imagesDir?.listFiles()?.filter { it.isFile } ?: emptyList()
            val videoFiles = videosDir?.listFiles()?.filter { it.isFile } ?: emptyList()

            val sortedImages = imageFiles.sortedByDescending { it.lastModified() }
            val sortedVideos = videoFiles.sortedByDescending { it.lastModified() }

            _imageItems.value = sortedImages.map { GalleryItem(it, MediaType.IMAGE) }
            _videoItems.value = sortedVideos.map { GalleryItem(it, MediaType.VIDEO) }
        }
    }

    fun refreshGalleryItems() {
        loadGalleryItems()  // Сделать этот метод публичным или создать аналогичный публичный метод
    }
}