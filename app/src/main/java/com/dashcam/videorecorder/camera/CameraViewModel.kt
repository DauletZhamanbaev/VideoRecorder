package com.dashcam.videorecorder.camera

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import com.dashcam.videorecorder.model.DetectionResult
import com.dashcam.videorecorder.data.RecordingFileManager
import com.dashcam.videorecorder.model.ModelInterface
import com.dashcam.videorecorder.model.TfLiteYoloModel
import androidx.camera.video.*
import com.dashcam.videorecorder.data.PhotoFileManager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class CameraViewModel(application: Application) : AndroidViewModel(application){
    private val appContext = application.applicationContext
    //Состояние записи
    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()
    //Результаты детекции
    private val _detectionResults = MutableStateFlow(emptyList<DetectionResult>())
    val detectionResults = _detectionResults.asStateFlow()

    private val _roadSignModel: ModelInterface = TfLiteYoloModel()
    val model: ModelInterface get() = _roadSignModel

    private var _activeRecording: Any? = null
    private val _qualitySelector = QualitySelector.fromOrderedList(
        listOf(Quality.FHD, Quality.HD, Quality.HIGHEST)
    )

    // Состояние текущего селектора камеры
    private val _cameraSelector = MutableStateFlow(CameraSelector.DEFAULT_BACK_CAMERA)
    val cameraSelector = _cameraSelector.asStateFlow()

    private val permissions = listOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
    )

    val videoCapture: VideoCapture<Recorder> by lazy {
        val recorder = Recorder.Builder()
            .setQualitySelector(_qualitySelector)
            .build()
        VideoCapture.withOutput(recorder)
    }

    val imageCapture: ImageCapture by lazy {
        ImageCapture.Builder()
            .setTargetResolution(android.util.Size(640, 480))
            .setTargetRotation(android.view.Surface.ROTATION_0)
            .build()
    }

    init {
        // Запуск загрузки модели
        viewModelScope.launch {
            _roadSignModel.loadModel(appContext)
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return permissions.all{
            ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun startRecording() {
        if (!hasRequiredPermissions()) {
            Toast.makeText(appContext, "Разрешения не предоставлены", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val file: File = RecordingFileManager.createVideoFile(appContext)
            val outputOptions = FileOutputOptions.Builder(file).build()

            val recorderOutput = videoCapture.output
                .prepareRecording(appContext, outputOptions)
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(appContext)) { recordEvent ->
                    when (recordEvent) {
                        is VideoRecordEvent.Start -> {
                            _isRecording.value = true
                        }

                        is VideoRecordEvent.Finalize -> {
                            _isRecording.value = false
                            _activeRecording = null
                            Toast.makeText(appContext, "Запись завершена", Toast.LENGTH_SHORT)
                                .show()
                        }

                        else -> {}
                    }
                }
            _activeRecording = recorderOutput
        } catch (e: SecurityException){
            e.printStackTrace()
        }
    }

    fun stopRecording() {
        // Останавливаем запись
        // (При окончании придёт VideoRecordEvent.Finalize -> _isRecording=false)
        if (_activeRecording != null) {
            (_activeRecording as? androidx.camera.video.Recording)?.stop()
        }
    }
    fun switchCamera() {
        _cameraSelector.value = if (_cameraSelector.value == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    // Вызывается при получении новых результатов детекции
    fun updateDetections(newDetections: List<DetectionResult>) {
        _detectionResults.update { newDetections }
    }

    fun takePhoto() {
        val photoFile = PhotoFileManager.createPhotoFile(appContext)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(appContext),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Toast.makeText(appContext, "Фото сохранено: ${photoFile.absolutePath}", Toast.LENGTH_SHORT).show()
                }
                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(appContext, "Ошибка съёмки фото: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    override fun onCleared() {
        super.onCleared()
        model.close() // Закрываем ресурсы модели
    }
}