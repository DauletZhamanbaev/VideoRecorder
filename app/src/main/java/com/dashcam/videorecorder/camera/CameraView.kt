package com.dashcam.videorecorder.camera

import androidx.compose.runtime.Composable

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.dashcam.videorecorder.camera.CameraViewModel
import com.dashcam.videorecorder.components.DetectionOverlay
import com.dashcam.videorecorder.components.CameraPreviewComposable
import com.dashcam.videorecorder.components.TopLeftIconsRow
import com.dashcam.videorecorder.components.BottomTransparentPanel


import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment

@Composable

fun CameraView(
    cameraViewModel: CameraViewModel
) {
    // Подписываемся на StateFlow
    val isRecording by cameraViewModel.isRecording.collectAsState()
    val detectionResults by cameraViewModel.detectionResults.collectAsState()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {

        // Превью + анализ
        CameraPreviewComposable(
            videoCapture = cameraViewModel.videoCapture,
            roadSignModel = cameraViewModel.model
        ) { newDetections ->
            cameraViewModel.updateDetections(newDetections)
        }

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
                onSwitchOrientation = { /* TODO */ },
                onSwitchCamera = { /* TODO */ }
            )
        }

        // Панель снизу
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            BottomTransparentPanel(
                onClickStartStop = {
                    if (!isRecording) cameraViewModel.startRecording()
                    else cameraViewModel.stopRecording()
                },
                onClickSettings = { /* TODO */ },
                onClickPhoto = { /* TODO */ },
                isRecording = isRecording
            )
        }
    }
}