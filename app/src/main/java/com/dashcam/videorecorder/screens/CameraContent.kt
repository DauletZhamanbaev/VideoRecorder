package com.dashcam.videorecorder.screens

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*

import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings

import androidx.compose.foundation.shape.RoundedCornerShape

import android.content.Context
import android.content.pm.PackageManager
import android.util.Size
import android.widget.Toast
import androidx.camera.core.ImageAnalysis
import androidx.camera.video.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.runtime.*

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dashcam.videorecorder.data.RecordingFileManager
import com.google.accompanist.permissions.isGranted
import java.util.concurrent.ExecutorService

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.dashcam.videorecorder.data.RoadSignAnalyzer
import java.util.Arrays


@Composable
fun CameraContent() {

    val context = LocalContext.current

    val permissions = listOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
    )

    fun hasRequiredPermissions():Boolean{
        return permissions.all{
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    var isRecording by remember { mutableStateOf(false) }
    // Активная запись (Recording), чтобы остановить при need
    var activeRecording by remember { mutableStateOf<Recording?>(null) }

    // Executor для CameraX (фон)
    val cameraExecutor: ExecutorService = remember {
        Executors.newSingleThreadExecutor()
    }

    val qualitySelector = QualitySelector.fromOrderedList(
            listOf(Quality.FHD, Quality.HD, Quality.HIGHEST)
    )
    // Создаём VideoCapture
    val videoCapture = remember {
        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build()
        VideoCapture.withOutput(recorder)
    }

    val mainExecutor = ContextCompat.getMainExecutor(context)

    // Начало записи
    fun startRecording() {
        if (!hasRequiredPermissions()) {
            return
        }
        val file = RecordingFileManager.createVideoFile(context)
        val outputOptions = FileOutputOptions.Builder(file).build()

        val recording = videoCapture.output
            .prepareRecording(context, outputOptions)
            .withAudioEnabled()
            .start(mainExecutor) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        // Началась запись
                        isRecording = true
                    }
                    is VideoRecordEvent.Finalize -> {
                        // Окончание записи
                        isRecording = false
                        activeRecording = null
                        Toast.makeText(context, "Запись завершена", Toast.LENGTH_SHORT).show()

                    }
                    // TODO события (Pause, Resume, Status)
                }
            }

        activeRecording = recording
    }

    // Конец записи
    fun stopRecording() {
        activeRecording?.stop() // Когда придёт VideoRecordEvent.Finalize, isRecording станет false
    }


    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreviewComposable(videoCapture)

        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            TopLeftIconsRow(
                onSwitchOrientation = { /* TODO: сделать смену ориентации */ },
                onSwitchCamera = { /* TODO: сделать смену камеры */ }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            BottomTransparentPanel(
                onClickStartStop = {
                    if (!isRecording) {
                        startRecording()
                    }
                    else {
                        stopRecording()
                    } },
                onClickSettings = { /* TODO */ },
                onClickPhoto = { /* TODO */ },
                isRecording = isRecording
            )
        }
    }

}

@Composable
fun CameraPreviewComposable(
    videoCapture: VideoCapture<Recorder>,
) {
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }

    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            // Можно установить TargetResolution, например 640x480,
            // чтобы не перегружать девайс
            .setTargetResolution(Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
    }
    val signAnalyzer = remember {
        RoadSignAnalyzer()
    }
    val context = LocalContext.current
    // executors для CameraX
    val mainExecutor = ContextCompat.getMainExecutor(context)
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember { PreviewView(context) }

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(true) {
        imageAnalysis.setAnalyzer(analyzerExecutor, signAnalyzer)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val provider = providerFuture.get() // блокируется, но LaunchedEffect - корутина
        cameraProvider = provider

        // после получения cameraProvider инициализируем preview + bind
        val previewUseCase = Preview.Builder().build()
        previewUseCase.setSurfaceProvider(previewView.surfaceProvider)

        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                previewUseCase,
                videoCapture
            )
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )
}



@Composable
fun TopLeftIconsRow(
    onSwitchOrientation: () -> Unit,
    onSwitchCamera: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 16.dp)
    ) {
        // Смена ориентации
        IconButton(
            onClick = { onSwitchOrientation() }
        ) {
            Icon(
                imageVector = Icons.Default.Repeat,
                contentDescription = "Switch orientation",
                tint = Color.White
            )
        }

        Spacer(Modifier.width(16.dp))

        // Смена камеры
        IconButton(
            onClick = { onSwitchCamera() }
        ) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = "Switch camera",
                tint = Color.White
            )
        }
    }
}

@Composable
fun BottomTransparentPanel(
    onClickStartStop: () -> Unit,
    onClickSettings: () -> Unit,
    onClickPhoto: () -> Unit,
    isRecording: Boolean
) {
    // Для прозрачности можно использовать semi-transparent Background
    Row(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .height(80.dp)
            .background(Color.Black.copy(alpha = 0.3f), shape = RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Кнопка настроек
        IconButton(onClick = { onClickSettings() }) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = Color.White
            )
        }

        // Кнопка записи видео
        IconButton(onClick = { onClickStartStop() }) {
            val icon = if (isRecording) Icons.Default.Stop else Icons.Default.Videocam
            Icon(
                imageVector = icon,
                contentDescription = "Start/Stop",
                tint = Color.White
            )
        }

        // Кнопка Фото
        IconButton(onClick = { onClickPhoto() }) {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = "Photo",
                tint = Color.White
            )
        }
    }
}