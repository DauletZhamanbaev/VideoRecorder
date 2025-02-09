package com.dashcam.videorecorder.model

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer

interface ModelInterface {
    fun loadModel(context: Context)

    // Run inference on a prepared input (e.g. ByteBuffer of size [1, height, width, channels])
    // Returns a list of detection results.
    fun runInference(inputData: ByteBuffer): List<DetectionResult>

    // Cleanup any resources (close interpreters, executors, etc.)
    fun close()
}

data class DetectionResult(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val confidence: Float,
    val classId: Int
)