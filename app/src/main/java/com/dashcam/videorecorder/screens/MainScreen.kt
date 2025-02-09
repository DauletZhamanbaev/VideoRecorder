package com.dashcam.videorecorder.screens

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dashcam.videorecorder.model.DetectionResult
import com.dashcam.videorecorder.model.TfLiteYoloModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState

import androidx.compose.foundation.Canvas

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.min
import kotlin.math.max

import com.google.accompanist.permissions.*


import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen() {
    // Набор нужных разрешений
    val context = LocalContext.current

    // Набор нужных разрешений
    val permissions = listOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
    )
    val multiplePermissionsState =
        rememberMultiplePermissionsState(permissions = permissions)

    val allPermissionsGranted =
        multiplePermissionsState.permissions.all { it.status.isGranted }

    if (!allPermissionsGranted) {
        // Если нет разрешений, просим их
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Требуются разрешения для использования камеры и записи аудио.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                multiplePermissionsState.launchMultiplePermissionRequest()
            }) {
                Text("Разрешить")
            }
        }
    } else {
        // Разрешения даны → инициализируем модель и переходим к CameraContent
        val roadSignModel = remember {
            TfLiteYoloModel() // или ваша реализация ModelInterface
        }

        // При первом показе Composable грузим модель (асинхронно)
        LaunchedEffect(true) {
            roadSignModel.loadModel(context)
        }

        // Храним результаты распознавания (если хотим их где-то показывать)
        var detectionResults by remember { mutableStateOf(emptyList<DetectionResult>()) }

        // Передаём модель и колбэк
        CameraContent(
            roadSignModel = roadSignModel,
            onDetections = { newDetections ->
                // Здесь можно обновить состояние или что-то еще
                detectionResults = newDetections
            }
        )
        Box(modifier = Modifier.fillMaxSize()) {
            DetectionOverlay(
                detectionList = detectionResults,
                previewWidth = 1280,
                previewHeight = 960,
                modifier = Modifier
                    .fillMaxSize()
            )
        }

    }
}

@Composable
fun DetectionOverlay(
    detectionList: List<DetectionResult>,
    previewWidth: Int,
    previewHeight: Int,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val scaleX = constraints.maxWidth.toFloat() / previewWidth
        val scaleY = constraints.maxHeight.toFloat() / previewHeight

        Canvas(modifier = Modifier.fillMaxSize()) {
            detectionList.forEach { det ->
                val left = det.x1 * scaleX
                val top = det.y1 * scaleY
                val right = det.x2 * scaleX
                val bottom = det.y2 * scaleY

                // Если x2 < x1 или y2 < y1, можно swap
                val boxLeft = min(left, right)
                val boxTop = min(top, bottom)
                val boxRight = max(left, right)
                val boxBottom = max(top, bottom)

                drawRect(
                    color = Color.Red.copy(alpha = 0.4f),
                    topLeft = Offset(boxLeft, boxTop),
                    size = Size(boxRight - boxLeft, boxBottom - boxTop),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}