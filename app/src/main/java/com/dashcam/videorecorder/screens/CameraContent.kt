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
import android.util.Log
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
import com.dashcam.videorecorder.model.DetectionResult
import java.util.Arrays

import com.dashcam.videorecorder.model.ModelInterface

import kotlin.math.max
import kotlin.math.min
import androidx.compose.foundation.Canvas


import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke

import androidx.compose.ui.geometry.Size as DrawSize


import com.google.accompanist.permissions.*

import android.view.Surface as ViewSurface


@Composable
fun CameraContent(
    roadSignModel: ModelInterface,
    onDetections: (List<DetectionResult>) -> Unit
) {

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
                    else -> {}
                    // TODO события (Pause, Resume, Status)
                }
            }

        activeRecording = recording
    }

    // Конец записи
    fun stopRecording() {
        activeRecording?.stop() // Когда придёт VideoRecordEvent.Finalize, isRecording станет false
    }

    var detectionResults by remember { mutableStateOf(emptyList<DetectionResult>()) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 1) Предпросмотр + анализ
        CameraPreviewComposable(
            videoCapture = videoCapture,
            roadSignModel = roadSignModel
        ) { newDetections ->
            detectionResults = newDetections
            onDetections(newDetections) // если хотим дополнительно пробросить
        }

        // 2) Overlay: рисуем bounding boxes поверх
        Box(modifier = Modifier.fillMaxSize()) {
            DetectionOverlay(
                detectionList = detectionResults,
                baseWidth = 640,
                baseHeight = 480,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 3) Иконки сверху слева
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            TopLeftIconsRow(
                onSwitchOrientation = { /* TODO */ },
                onSwitchCamera = { /* TODO */ }
            )
        }

        // 4) Панель снизу
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            BottomTransparentPanel(
                onClickStartStop = {
                    if (!isRecording) startRecording() else stopRecording()
                },
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
    roadSignModel: ModelInterface,
    onDetections: (List<DetectionResult>) -> Unit,
) {
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val roadSignAnalyzer = remember {
        RoadSignAnalyzer(roadSignModel) { detections ->
            onDetections(detections)
        }
    }
    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 480))
            .setTargetRotation(ViewSurface.ROTATION_0) // всегда 0°
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(analyzerExecutor, roadSignAnalyzer)
            }
    }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember { PreviewView(context) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(true) {

        val providerFuture = ProcessCameraProvider.getInstance(context)
        val provider = providerFuture.get()
        cameraProvider = provider

        // после получения cameraProvider инициализируем preview + bind
        val previewUseCase = Preview.Builder()
            .setTargetResolution(android.util.Size(1280, 720)) // for example
            .build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }
        previewUseCase.setSurfaceProvider(previewView.surfaceProvider)

        provider.unbindAll()
        try {
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                previewUseCase,
                imageAnalysis
            )
        } catch(e: Exception) {
            e.printStackTrace()
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

@Composable
fun DetectionOverlay(
    detectionList: List<DetectionResult>,
    baseWidth: Int = 640,
    baseHeight: Int = 480,
    modifier: Modifier = Modifier
) {
    // full screen container
    BoxWithConstraints(modifier = modifier) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()

        // camera aspect = 640/480 = 1.3333
        val cameraAspect = baseWidth.toFloat() / baseHeight
        val screenAspect = screenW / screenH

        // Определяем scale, offset
        val scale: Float
        val offsetX: Float
        val offsetY: Float

        if (screenAspect > cameraAspect) {
            // ширина экрана слишком «большая», height лимитирует
            scale = screenH / baseHeight
            val newWidth = baseWidth * scale
            offsetX = (screenW - newWidth) / 2f
            offsetY = 0f
        } else {
            // высота экрана слишком «большая», width лимитирует
            scale = screenW / baseWidth
            val newHeight = baseHeight * scale
            offsetX = 0f
            offsetY = (screenH - newHeight) / 2f
        }

        // Теперь рисуем
        Canvas(modifier = Modifier.fillMaxSize()) {
            detectionList.forEach { det ->
                // берем координаты x1,y1,x2,y2
                val x1 = det.x1
                val y1 = det.y1
                val x2 = det.x2
                val y2 = det.y2

                // swap left/right top/bottom
                val left = min(x1, x2)
                val right= max(x1, x2)
                val top  = min(y1, y2)
                val bottom = max(y1, y2)

                // scale + offset
                val leftPx = offsetX + left * scale
                val rightPx= offsetX + right* scale
                val topPx  = offsetY + top * scale
                val bottomPx= offsetY + bottom* scale

                Log.d("Overlay","Box coords:($leftPx,$topPx)->($rightPx,$bottomPx), scale=$scale offset=($offsetX,$offsetY)")

                drawRect(
                    color = Color.Red.copy(alpha=0.4f),
                    topLeft = Offset(leftPx, topPx),
                    size = DrawSize(rightPx - leftPx, bottomPx - topPx),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}