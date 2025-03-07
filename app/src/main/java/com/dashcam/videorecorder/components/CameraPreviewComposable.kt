package com.dashcam.videorecorder.components

import android.util.Size
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.dashcam.videorecorder.model.ModelInterface
import com.dashcam.videorecorder.data.RoadSignAnalyzer
import com.dashcam.videorecorder.data.RoadSignClassifier
import java.util.concurrent.Executors
import androidx.camera.video.*
import androidx.compose.foundation.layout.fillMaxSize

import com.dashcam.videorecorder.model.DetectionResult



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
        RoadSignAnalyzer(
            model = roadSignModel,
            classifier = RoadSignClassifier(context),
            context = context
        ) { detections ->
            onDetections(detections)
        }
    }
    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setTargetRotation(Surface.ROTATION_0) // всегда 0°
            .setTargetResolution(Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(analyzerExecutor, roadSignAnalyzer) }
    }
    val previewView = remember { PreviewView(context) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(true) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val provider = providerFuture.get()
        cameraProvider = provider
        provider.unbindAll()

        val previewUseCase = Preview.Builder()
            .setTargetRotation(Surface.ROTATION_0) // всегда 0°
            .setTargetResolution(Size(640, 480))
            .build()
            .apply { setSurfaceProvider(previewView.surfaceProvider) }

        previewUseCase.surfaceProvider = previewView.surfaceProvider
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