package com.dashcam.videorecorder.camera

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import com.dashcam.videorecorder.model.DetectionResult
import com.dashcam.videorecorder.data.RecordingFileManager
import com.dashcam.videorecorder.model.ModelInterface
import com.dashcam.videorecorder.model.TfLiteYoloModel
import androidx.camera.video.*

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

    // Вызывается при получении новых результатов детекции
    fun updateDetections(newDetections: List<DetectionResult>) {
        _detectionResults.update { newDetections }
    }

    override fun onCleared() {
        super.onCleared()
        model.close() // Закрываем ресурсы модели
    }
}