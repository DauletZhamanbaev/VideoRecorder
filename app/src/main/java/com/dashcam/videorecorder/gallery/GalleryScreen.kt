@file:Suppress("IMPLICIT_CAST_TO_ANY")

package com.dashcam.videorecorder.gallery

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.dashcam.videorecorder.gallery.GalleryItem
import com.dashcam.videorecorder.gallery.MediaType
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    galleryViewModel: GalleryViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onBack: () -> Unit
) {
    val imageItems by galleryViewModel.imageItems.collectAsState()
    val videoItems by galleryViewModel.videoItems.collectAsState()
    val context = LocalContext.current

    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("Images", "Videos")

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
        Column(modifier = Modifier.padding(paddingValues)) {
            // Вкладки для переключения между фото и видео
            TabRow(selectedTabIndex = selectedTab) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            when (selectedTab) {
                0 -> GalleryGrid(
                    items = imageItems,
                    mediaType = MediaType.IMAGE,
                    onDelete = { item ->
                        deleteFile(item, context)
                        galleryViewModel.refreshGalleryItems() // обновляем список
                    },
                    onCopy = { item ->
                        copyFileToDownloads(item, context)
                    }
                )
                1 -> GalleryGrid(
                    items = videoItems,
                    mediaType = MediaType.VIDEO,
                    onDelete = { item ->
                        deleteFile(item, context)
                        galleryViewModel.refreshGalleryItems() // обновляем список
                    },
                    onCopy = { item ->
                        copyFileToDownloads(item, context)
                    }
                )
            }
        }
    }
}

@Composable
fun GalleryGrid(
    items: List<GalleryItem>,
    mediaType: MediaType,
    onDelete: (GalleryItem) -> Unit,
    onCopy: (GalleryItem) -> Unit
) {
    val context = LocalContext.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 128.dp),
        contentPadding = PaddingValues(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items.size) { index ->
            val item = items[index]
            var expanded by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .aspectRatio(1f)
            ) {
                // Формирование модели для AsyncImage:
                // Если это видео, извлекаем кадр с помощью videoFrameMillis, иначе передаем файл напрямую
                val model = if (mediaType == MediaType.VIDEO) {
                    ImageRequest.Builder(context)
                        .data(Uri.fromFile(item.file))
                        .videoFrameMillis(500)
                        .decoderFactory { result, options, _ ->
                            VideoFrameDecoder(
                                result.source,
                                options
                            )
                        }
                        .build()
                } else {
                    item.file
                }
                // Превью с обработчиком клика для открытия файла
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            // Получаем URI через FileProvider
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.provider",
                                item.file
                            )
                            val mimeType = if (mediaType == MediaType.IMAGE) "image/*" else "video/*"
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, mimeType)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(context, "Не удалось открыть файл", Toast.LENGTH_SHORT).show()
                            }
                        }
                )
                // Если это видео, накладываем иконку воспроизведения
                if (mediaType == MediaType.VIDEO) {
                    Icon(
                        imageVector = Icons.Default.PlayCircleFilled,
                        contentDescription = "Video",
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(24.dp)
                    )
                }
                // Кнопка вызова выпадающего меню (удаление / копирование)
                IconButton(
                    onClick = { expanded = true },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Опции"
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Удалить") },
                        onClick = {
                            expanded = false
                            onDelete(item)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Копировать в загрузки") },
                        onClick = {
                            expanded = false
                            onCopy(item)
                        }
                    )
                }
            }
        }
    }
}

fun deleteFile(item: GalleryItem, context: Context) {
    if (item.file.delete()) {
        Toast.makeText(context, "Файл удалён", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, "Не удалось удалить файл", Toast.LENGTH_SHORT).show()
    }
}

fun copyFileToDownloads(item: GalleryItem, context: Context) {
    try {
        // Определяем папку "Загрузки"
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val destinationFile = File(downloadsDir, item.file.name)
        // Копируем файл (переопределяем, если файл уже существует)
        item.file.copyTo(destinationFile, overwrite = true)
        Toast.makeText(context, "Файл скопирован в загрузки", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Ошибка копирования файла", Toast.LENGTH_SHORT).show()
    }
}