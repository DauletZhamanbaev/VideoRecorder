package com.dashcam.videorecorder.camera

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.util.Log
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
import com.dashcam.videorecorder.settings.SettingsData
import com.dashcam.videorecorder.settings.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.LinkedList
import java.util.Queue

sealed class NavigationEvent {
    object ToGallery : NavigationEvent()
    object ToSettings : NavigationEvent()
}

class CameraViewModel(application: Application) : AndroidViewModel(application){
    private val appContext = application.applicationContext

    private val settingsRepository = SettingsRepository(appContext)

    // Подписываемся на изменения настроек
    val settingsFlow = settingsRepository.settingsFlow.stateIn(
        viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.Eagerly,
        initialValue = SettingsData(
            videoResolution = "FHD",
            bitrate = 4000000,
            fps = 30,
            maxMemory = 500,
            circularRecording = false,
            maxDuration = 60,
            roadSignRecognition = true
        )
    )

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

    private val _classifiedSignFlow = MutableSharedFlow<Int>(replay = 0)
    val classifiedSignFlow = _classifiedSignFlow.asSharedFlow()

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

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent = _navigationEvent.asSharedFlow()

    init {
        // Запуск загрузки модели
        viewModelScope.launch {
            _roadSignModel.loadModel(appContext)
        }
    }
    private val signQueue: Queue<Int> = LinkedList()
    private var mediaPlayer: MediaPlayer? = null

    private val lastAddedTime = mutableMapOf<Int, Long>()
    private val minRepeatIntervalMs = 5_000L
    private var autoStopJob: Job? = null
    private var userStopped = false

    private var isStart = false

    /**
     * Проиграть аудио "signs/{classId}.wav" из assets/signs/
     */
    private fun playSignAudioFromAssets(classId: Int) {
        val filePath = "signs/$classId.wav"

        try {
            val afd = appContext.assets.openFd(filePath)
            val mp = MediaPlayer()
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()

            mp.prepare()
            mp.start()
            mediaPlayer = mp

            mp.setOnCompletionListener { player ->
                player.release()
                if (mediaPlayer == player) {
                    mediaPlayer = null
                }
                tryPlayNext()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            mediaPlayer = null
            tryPlayNext()
        }
    }


    private fun hasRequiredPermissions(): Boolean {
        return permissions.all{
            ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private suspend fun waitUntilRecorderCleared(timeoutMs: Long = 3000L) {
        val startTime = System.currentTimeMillis()
        while (_activeRecording != null && System.currentTimeMillis() - startTime < timeoutMs) {
            delay(100)
        }
        Log.d("CameraViewModel", "waitUntilRecorderCleared() finished: activeRecording=${_activeRecording != null}")
    }

    fun startRecording() {
        if (!hasRequiredPermissions()) {
            Toast.makeText(appContext, "Разрешения не предоставлены", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("CameraViewModel", "startRecording() called. " +
                "isRecording=${_isRecording.value}, " +
                "activeRecording=${_activeRecording != null}, " +
                "userStopped=$userStopped")

        if (_isRecording.value) {
            Log.w("CameraViewModel", "Called startRecording() but isRecording is true, skip!")
            return
        }


        userStopped = false

        try {
            viewModelScope.launch {
                if (_activeRecording != null) {
                    Log.w("CameraViewModel", "activeRecording != null, calling stopRecording(userInitiated=true)")
                    stopRecording(userInitiated = true)
                    waitUntilRecorderCleared()
                }

                val settings = settingsFlow.first()

                val file: File = RecordingFileManager.createVideoFile(appContext)
                val outputOptions = FileOutputOptions.Builder(file).build()

                val recorderOutput = videoCapture.output
                    .prepareRecording(appContext, outputOptions)
                    .withAudioEnabled()
                    .start(ContextCompat.getMainExecutor(appContext)) { recordEvent ->
                        when (recordEvent) {
                            is VideoRecordEvent.Start -> {
                                _isRecording.value = true

                                if (settings.maxDuration > 0) {
                                    autoStopJob?.cancel()
                                    autoStopJob = viewModelScope.launch {
                                        delay(settings.maxDuration * 1000L)
                                        if (_isRecording.value) {
                                            stopRecording(userInitiated = false)
                                        }
                                    }
                                }
                            }

                            is VideoRecordEvent.Finalize -> {
                                _isRecording.value = false
                                _activeRecording = null

                                autoStopJob?.cancel()
                                autoStopJob = null

                                RecordingFileManager.addRecordedFile(file)

                                Toast.makeText(appContext, "Запись завершена", Toast.LENGTH_SHORT).show()

                                // [NEW] «Подход A»: сразу же сканируем папку
                                //       и, если превышен maxMemory, удаляем старые файлы.
                                if (settings.circularRecording) {
                                    RecordingFileManager.checkAndCleanup(appContext, settings.maxMemory)

                                    if (!userStopped) {
                                        startRecording()  // Запускаем новый «сегмент»
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                _activeRecording = recorderOutput
            }
        } catch (e: SecurityException){
            e.printStackTrace()
        }
    }
    fun stopRecording(userInitiated: Boolean = true) {
        userStopped = userInitiated

        autoStopJob?.cancel()
        autoStopJob = null

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

    fun openGallery() {
        viewModelScope.launch {
            _navigationEvent.emit(NavigationEvent.ToGallery)
        }
    }

    fun openSettings() {
        stopRecording(userInitiated = false)
        viewModelScope.launch {
            val startWait = System.currentTimeMillis()
            while (_isRecording.value || _activeRecording != null) {
                if (System.currentTimeMillis() - startWait > 2000) break
                delay(100)
            }
            _navigationEvent.emit(NavigationEvent.ToSettings)
        }
    }

    private fun tryPlayNext() {
        if (mediaPlayer != null) return
        val nextSign = signQueue.poll() ?: return
        playSignAudioFromAssets(nextSign)
    }

    fun onSignClassified(classId: Int) {
        val now = System.currentTimeMillis()

        val lastTime = lastAddedTime[classId] ?: 0L
        if (now - lastTime < minRepeatIntervalMs) {
            // Если прошло меньше, чем minRepeatIntervalMs, пропускаем
            return
        }

        lastAddedTime[classId] = now

        signQueue.offer(classId)
        tryPlayNext()

        viewModelScope.launch {
            _classifiedSignFlow.emit(classId)
        }
    }


    override fun onCleared() {
        super.onCleared()
        stopRecording(false)
        model.close() // Закрываем ресурсы модели
        mediaPlayer?.release()
        mediaPlayer = null
        signQueue.clear()
    }

}