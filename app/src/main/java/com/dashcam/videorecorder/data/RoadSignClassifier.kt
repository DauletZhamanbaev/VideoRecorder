package com.dashcam.videorecorder.data

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel


class RoadSignClassifier(
    context: Context,
    private val inputWidth: Int = 32,
    private val inputHeight: Int = 32,
    private val nChannels: Int = 3
) {

    private var tfLite: Interpreter? = null
    private var tfliteModel: ByteBuffer? = null

    private var numClasses: Int = 0

    // Временное хранилище входа
    private val inputSizeBytes = inputWidth * inputHeight * nChannels
    private val inputBuffer: ByteBuffer

    // Выход (quant8) [nClasses] байт
    private val outputBuffer: ByteBuffer

    init {
        // 1) Загружаем модель (название модели замените при необходимости)
        val modelData = loadModelFile(context.assets, "model_640x480_quantized.tflite")

        // 2) Создаём Interpreter без GPU
        val options = Interpreter.Options().apply {
            setNumThreads(4)
            setUseXNNPACK(true)
        }
        tfLite = Interpreter(modelData, options)
        tfliteModel = modelData

        // Определяем число классов из выходного тензора
        val outShape = tfLite?.getOutputTensor(0)?.shape() ?: intArrayOf(1, 10)
        // outShape[1] = nClasses
        numClasses = if (outShape.size == 2) outShape[1] else 10

        // 3) Готовим inputBuffer / outputBuffer
        inputBuffer = ByteBuffer.allocateDirect(inputSizeBytes).apply {
            order(ByteOrder.nativeOrder())
        }
        outputBuffer = ByteBuffer.allocateDirect(numClasses).apply {
            order(ByteOrder.nativeOrder())
        }

        Log.d("RoadSignClassifier", "numClasses=$numClasses (CPU only)")
    }

    /**
     * Закрыть ресурсы
     */
    fun close() {
        tfLite?.close()
        tfLite = null
        tfliteModel = null
    }

    /**
     * Классифицировать ROI (уже [32×32×3] raw bytes [0..255]).
     * Возвращаем int classId (argmax).
     */
    fun classifyROI(roiBuffer: ByteBuffer): Int {
        val interpreter = tfLite ?: return -1

        // 1) Копируем roi -> input
        inputBuffer.rewind()
        roiBuffer.rewind()
        if (roiBuffer.remaining() != inputSizeBytes) {
            Log.e("RoadSignClassifier", "Wrong ROI size: got=${roiBuffer.remaining()}, expected=$inputSizeBytes")
            return -1
        }
        inputBuffer.put(roiBuffer)
        inputBuffer.rewind()

        // 2) Чистим выход
        outputBuffer.rewind()
        // 3) run
        interpreter.run(inputBuffer, outputBuffer)
        outputBuffer.rewind()

        // 4) Ищем max
        var maxIdx = -1
        var maxVal = 0
        for (i in 0 until numClasses) {
            var bVal = outputBuffer.get(i) // byte => [-128..127]
            var valInt = bVal.toInt()
            if (valInt < 0) valInt += 256 // => [0..255]
            if (valInt > maxVal) {
                maxVal = valInt
                maxIdx = i
            }
        }
        return maxIdx
    }

    /**
     * Загрузить модель как MappedByteBuffer из assets
     */
    @Throws(IOException::class)
    private fun loadModelFile(assetManager: AssetManager, modelFilename: String): ByteBuffer {
        val fileDescriptor: AssetFileDescriptor = assetManager.openFd(modelFilename)
        FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        }
    }
}
