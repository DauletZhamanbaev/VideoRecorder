package com.dashcam.videorecorder.camera

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.dashcam.videorecorder.camera.CameraViewModel
import com.dashcam.videorecorder.components.DetectionOverlay
import com.dashcam.videorecorder.components.CameraPreviewComposable
import com.dashcam.videorecorder.components.TopLeftIconsRow
import com.dashcam.videorecorder.components.BottomTransparentPanel


import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity

import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun CameraView(
    cameraViewModel: CameraViewModel
) {
    // Подписываемся на StateFlow
    val isRecording by cameraViewModel.isRecording.collectAsState()
    val detectionResults by cameraViewModel.detectionResults.collectAsState()
    val cameraSelector by cameraViewModel.cameraSelector.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    var lastSignId by remember { mutableStateOf<Int?>(null) }
    var lastShownTimeMs by remember { mutableStateOf(0L) }

    // Текущий «активный» (отображаемый) знак для всплывающего окна
    var currentSignLabel by remember { mutableStateOf<String?>(null) }
    var showNotification by remember { mutableStateOf(false) }

    // Подписка на поток результатов классификации
    LaunchedEffect(Unit) {
        cameraViewModel.classifiedSignFlow.collect { classId ->
            val now = System.currentTimeMillis()
            // 2-секундная пауза между одинаковыми знаками (можете менять)
            val minIntervalMs = 2000L

            if (classId != lastSignId || (now - lastShownTimeMs) > minIntervalMs) {
                // Обновляем
                lastSignId = classId
                lastShownTimeMs = now
                val signName = mapClassIdToSignName(classId)
                if (signName != null) {
                    currentSignLabel = signName
                    showNotification = true
                    launch {
                        delay(2000)
                        showNotification = false
                    }
                }
            } else {
                // Если то же самое пришло слишком рано — игнорируем
            }
        }
    }


    var isLandscape by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {

            // Превью + анализ
            CameraPreviewComposable(
                videoCapture = cameraViewModel.videoCapture,
                roadSignModel = cameraViewModel.model,
                cameraSelector = cameraViewModel.cameraSelector.value,
                imageCapture = cameraViewModel.imageCapture,
                // Передаём сюда, что делать при получении детекций
                onDetections = { newDetections -> cameraViewModel.updateDetections(newDetections) },
                // Передаём сюда, что делать при классификации
                onClassification = { signClassId -> cameraViewModel.onSignClassified(signClassId) }
            )

            // Оверлей
            Box(modifier = Modifier.fillMaxSize()) {
                DetectionOverlay(
                    detectionList = detectionResults,
                    modifier = Modifier.fillMaxSize(),
                    screenRotationDeg = -90
                )
            }

            // Иконки верхнего левого угла
            Box(modifier = Modifier.fillMaxSize()) {
                TopLeftIconsRow(
                    onSwitchOrientation = { isLandscape = !isLandscape },
                    onSwitchCamera = { cameraViewModel.switchCamera() },
                    onOpenGallery = { cameraViewModel.openGallery() },
                    isLandscape = isLandscape
                )
            }

            // Панель снизу
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                BottomTransparentPanel(
                    onClickStartStop = {
                        if (!isRecording) cameraViewModel.startRecording()
                        else cameraViewModel.stopRecording()
                    },
                    onClickSettings = { cameraViewModel.openSettings() },
                    onClickPhoto = { cameraViewModel.takePhoto() },
                    isRecording = isRecording,
                    isLandscape = isLandscape
                )
            }

            val notificationModifier = if (!isLandscape) {
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp)
            } else {
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 0.dp, end = 0.dp)
                    .offset(y = (-100).dp, x = (70).dp)
                    .rotate(90f)
            }

            AnimatedVisibility(
                visible = showNotification && currentSignLabel != null,
                modifier = notificationModifier
            ) {
                Card(
                    modifier = Modifier.wrapContentSize(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Text(
                        text = currentSignLabel ?: "",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

// функция сопоставления classId -> строка
fun mapClassIdToSignName(classId: Int): String? {
    return when (classId) {
        0 -> "Пешеходный переход"
        1 -> "Ограничение скорости 5 км"
        2 -> "Ограничение скорости 10 км"
        3 -> "Ограничение скорости 20 км"
        4 -> "Ограничение скорости 30 км"
        5 -> "Ограничение скорости 40 км"
        6 -> "Ограничение скорости 50 км"
        7 -> "Ограничение скорости 60 км"
        8 -> "Ограничение скорости 70 км"
        9 -> "Ограничение скорости 80 км"
        10 -> "Ограничение скорости 90 км"
        11 -> "Ограничение скорости 100 км"
        12 -> "Ограничение скорости 110 км"
        13 -> "Ограничение скорости 120 км"
        14 -> "Искусственная неровность"
        15 -> "Движение запрещено"
        16 -> "Движение грузовых автомобилей запрещено"
        17 -> "Обгон запрещен"
        18 -> "Надземный пешеходный переход"
        19 -> "Подземный пешеходный переход"
        20 -> "Одностороннее движение"
        21 -> "Движение мотоциклов запрещено"
        22 -> "Автобусная полоса"
        23 -> "Ограничение высоты 1.8 м"
        24 -> "Ограничение высоты 4.5 м"
        25 -> "Поворот запрещен"
        26 -> "Разворот запрещен"
        27 -> "Движение ТС с опасными грузами"

        else -> null    }
}