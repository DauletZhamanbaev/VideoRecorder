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

    private val _galleryItems = MutableStateFlow<List<GalleryItem>>(emptyList())
    val galleryItems: StateFlow<List<GalleryItem>> = _galleryItems.asStateFlow()

    init {
        loadGalleryItems()
    }

    private fun loadGalleryItems() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            // Получаем каталоги для фото и видео
            val imagesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val videosDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            val imageFiles = imagesDir?.listFiles()?.filter { it.isFile } ?: emptyList()
            val videoFiles = videosDir?.listFiles()?.filter { it.isFile } ?: emptyList()
            // Создаем список GalleryItem
            val items = mutableListOf<GalleryItem>()
            items.addAll(imageFiles.map { GalleryItem(it, MediaType.IMAGE) })
            items.addAll(videoFiles.map { GalleryItem(it, MediaType.VIDEO) })
            // Опционально: сортировка по времени модификации (новейшие сверху)
            val sortedItems = items.sortedByDescending { it.file.lastModified() }
            _galleryItems.value = sortedItems
        }
    }
}