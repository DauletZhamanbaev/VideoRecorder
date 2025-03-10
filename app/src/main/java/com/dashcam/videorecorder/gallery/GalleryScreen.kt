package com.dashcam.videorecorder.gallery

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.dashcam.videorecorder.gallery.GalleryItem
import com.dashcam.videorecorder.gallery.MediaType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    galleryViewModel: GalleryViewModel = viewModel(),
    onBack: () -> Unit
) {
    val galleryItems by galleryViewModel.galleryItems.collectAsState()
    val context = LocalContext.current


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gallery") },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        // Для сетки используем LazyVerticalGrid (из androidx.compose.foundation.lazy.grid)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 128.dp),
            contentPadding = PaddingValues(8.dp),
            modifier = Modifier.padding(paddingValues)
        ) {
            items(galleryItems) { item ->
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .aspectRatio(1f)
                        .clickable {
                            // Запускаем внешний плеер/галерею для файла
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                // Если требуется использовать FileProvider, замените Uri.fromFile(...) на FileProvider.getUriForFile(...)
                                setDataAndType(Uri.fromFile(item.file), if (item.mediaType == MediaType.IMAGE) "image/*" else "video/*")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                ) {
                    // Загрузка изображения с помощью Coil
                    AsyncImage(
                        model = item.file,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (item.mediaType == MediaType.VIDEO) {
                        // Для видео можно добавить оверлей с иконкой воспроизведения
                        Icon(
                            imageVector = Icons.Default.PlayCircleFilled,
                            contentDescription = "Video",
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(24.dp)
                        )
                    }
                }
            }
        }
    }
}