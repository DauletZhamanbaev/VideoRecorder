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
import com.dashcam.videorecorder.data.RoadSignClassifier

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

        Box(modifier = Modifier.fillMaxSize()) {
            DetectionOverlay(
                detectionList = detectionResults,
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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val roadSignAnalyzer = remember {
        RoadSignAnalyzer(model = roadSignModel, classifier = RoadSignClassifier(context), context = context) { detections ->
            onDetections(detections)
        }
    }
    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 480)) //надо будет посмотреть
            .setTargetRotation(ViewSurface.ROTATION_0) // всегда 0°
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(analyzerExecutor, roadSignAnalyzer)
            }
    }


    val previewView = remember { PreviewView(context) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(true) {

        val providerFuture = ProcessCameraProvider.getInstance(context)
        val provider = providerFuture.get()
        cameraProvider = provider

        // после получения cameraProvider инициализируем preview + bind
        val previewUseCase = Preview.Builder()
            .setTargetResolution(android.util.Size(640, 480)) // for example
            .setTargetRotation(ViewSurface.ROTATION_0) // всегда 0°
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
                videoCapture,
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

//Повторяется, надо вынести в отдельный сервисный класс
fun transformBbox(
    x1: Float, y1: Float,
    x2: Float, y2: Float,
    xMin: Float, xMax: Float,
    yMin: Float, yMax: Float,
    targetW: Float,
    targetH: Float
): Quad<Float,Float,Float,Float> {
    val rx1 = minOf(x1,x2)
    val rx2 = maxOf(x1,x2)
    val ry1 = minOf(y1,y2)
    val ry2 = maxOf(y1,y2)

    // Простой линейный scale по X (без инверсии)
    fun scaleX(x: Float): Float {
        // если xMin=0, xMax=440, то x' = (x/440)*640
        return ((x - xMin)/(xMax - xMin)) * targetW
    }
    // Простой линейный scale по Y (без инверсии)
    fun scaleY(y: Float): Float {
        // если yMin=25, yMax=310, то y' = ((y-25)/(310-25))*480
        return ((y - yMin)/(yMax - yMin)) * targetH
    }

    // Преобразуем углы
    val nx1 = scaleX(rx1)
    val nx2 = scaleX(rx2)
    val ny1 = scaleY(ry1)
    val ny2 = scaleY(ry2)

    return Quad(nx1, ny1, nx2, ny2)
}

data class Quad<A,B,C,D>(val first: A, val second: B, val third: C, val fourth: D)

fun doSwapAndInvert(x: Float, y: Float, screenW: Float, screenH: Float): Pair<Float,Float> {
    // swap => newX= y, newY= x
    val newX = y
    val newY = x
    // invert X => finalX= screenH - newX, finalY= newY
    val fx = screenH - newX
    val fy = newY
    return Pair(fx, fy)
}

fun doSwapAndInvertY(x: Float, y: Float, screenW: Float, screenH: Float): Pair<Float,Float> {
    val newX = y
    val newY = x
    val fx = newX
    val fy = screenW - newY
    return Pair(fx, fy)
}

fun rotateOrSwapIfNeeded(
    sx1: Float, sy1: Float,
    sx2: Float, sy2: Float,
    screenW: Float,
    screenH: Float,
    rotationDeg: Int
): Quad<Float,Float,Float,Float> {

    return when (rotationDeg) {
        0 -> {
            // без изменений
            Quad(sx1, sy1, sx2, sy2)
        }
        -90, 270 -> {
            // swapAxes + invert X => px = screenH - sy, py = sx
            // (т.е. мы "кладём" bbox на бок)
            val (fx1, fy1) = doSwapAndInvert(sx1, sy1, screenW, screenH)
            val (fx2, fy2) = doSwapAndInvert(sx2, sy2, screenW, screenH)
            Quad(fx1, fy1, fx2, fy2)
        }
        180 -> {
            // Повернуть на 180 => (x,y)->(screenW-x, screenH-y) -
            //   если хотим центральный поворот,
            //   можно (cx-x,cy-y).
            val fx1 = screenW - sx1
            val fy1 = screenH - sy1
            val fx2 = screenW - sx2
            val fy2 = screenH - sy2
            Quad(fx1, fy1, fx2, fy2)
        }
        90 -> {
            // 90 => swapAxes + invert Y?
            val (fx1, fy1) = doSwapAndInvertY(sx1, sy1, screenW, screenH)
            val (fx2, fy2) = doSwapAndInvertY(sx2, sy2, screenW, screenH)
            Quad(fx1, fy1, fx2, fy2)
        }
        else -> {
            // если хотим arbitrary angle,
            //   делаем rotateAroundCenter(...)
            Quad(sx1, sy1, sx2, sy2)
        }
    }
}

@Composable
fun DetectionOverlay(
    detectionList: List<DetectionResult>,
    modifier: Modifier = Modifier,
    screenRotationDeg: Int = -90
) {
    if (detectionList.size == 0)
        return
    val xMin = 0f
    val xMax = 440f
    val yMin = 25f
    val yMax = 310f

    BoxWithConstraints(modifier = modifier) {
        val screenW: Float
        val screenH: Float

        if (screenRotationDeg in setOf(-90, 90, 270, -270)) {
            screenH = constraints.maxWidth.toFloat()
            screenW = constraints.maxHeight.toFloat()
        }
        else{
            screenH = constraints.maxHeight.toFloat()
            screenW = constraints.maxWidth.toFloat()
        }

        // Рисуем
        Canvas(modifier = Modifier.fillMaxSize()) {
            detectionList.forEach { det ->

                // Масштабирование
                val (sx1, sy1, sx2, sy2) = transformBbox(
                    det.x1, det.y1, det.x2, det.y2,
                    xMin=xMin, xMax=xMax,
                    yMin=yMin, yMax=yMax,
                    targetW= screenW,
                    targetH= screenH
                )

                val (fx1, fy1, fx2, fy2) = rotateOrSwapIfNeeded(
                    sx1, sy1, sx2, sy2,
                    screenW, screenH,
                    screenRotationDeg
                )

                val left   = min(fx1, fx2)
                val right  = max(fx1, fx2)
                val top    = min(fy1, fy2)
                val bottom = max(fy1, fy2)

                Log.d("Box Size", "final left - ${left}, finalRight - ${right}, finalTop -${top}, finalBottom - ${bottom}" )

                // Рисуем прямоугольник
                drawRect(
                    color = Color.Red.copy(alpha = 0.4f),
                    topLeft = Offset(left, top),
                    size = DrawSize(right - left, bottom - top),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}