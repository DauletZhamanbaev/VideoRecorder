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

import com.dashcam.videorecorder.camera.CameraView

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dashcam.videorecorder.camera.CameraViewModel

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(cameraViewModel: CameraViewModel = viewModel()) {
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

          CameraView(cameraViewModel)
//        val roadSignModel = remember {
//            TfLiteYoloModel()
//        }
//
//        LaunchedEffect(true) {
//            roadSignModel.loadModel(context)
//        }
//
//        var detectionResults by remember { mutableStateOf(emptyList<DetectionResult>()) }
//
//        // Передаём модель и колбэк
//        CameraContent(
//            roadSignModel = roadSignModel,
//            onDetections = { newDetections ->
//                // Здесь можно обновить состояние или что-то еще
//                detectionResults = newDetections
//            }
//        )

    }
}

