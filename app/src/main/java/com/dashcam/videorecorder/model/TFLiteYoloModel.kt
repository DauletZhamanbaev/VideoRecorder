package com.dashcam.videorecorder.model

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import com.dashcam.videorecorder.model.ModelInterface
import java.io.FileInputStream
import java.nio.channels.FileChannel

import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min


class TfLiteYoloModel : ModelInterface {

    private var tflite: Interpreter? = null

    // Для 640×480
    private val inputSizeBytes = 640 * 480 * 3 // 921600
    private val confThreshold = 0.5f
    private val iouThreshold = 0.45f

    override fun loadModel(context: Context) {
        val asset = context.assets.open("model_640x480.tflite")
        val modelBytes = asset.readBytes()
        asset.close()

        val modelBuffer = ByteBuffer.allocateDirect(modelBytes.size).order(ByteOrder.nativeOrder())
        modelBuffer.put(modelBytes)
        modelBuffer.rewind()

        val options = Interpreter.Options()
        tflite = Interpreter(modelBuffer, options)
    }

    override fun runInference(inputData: ByteBuffer): List<DetectionResult> {
        check(tflite != null) { "TfLite interpreter not initialized." }

        // Проверка длины
        if (inputData.remaining() != inputSizeBytes) {
            throw IllegalArgumentException("Ожидалось $inputSizeBytes, а получили ${inputData.remaining()}")
        }

        // Допустим, модель выдаёт [1, 18900, 6], но quant8. => объявим ByteArray(6).
        val rawOutput = Array(1) {
            Array(18900) {
                ByteArray(6) // quant8
            }
        }

        // Запуск
        tflite!!.run(inputData, rawOutput)

        val prelim = mutableListOf<DetectionResult>()

        val arr = rawOutput[0] // (18900, ByteArray(6))
        for (i in arr.indices) {
            val data = arr[i] // ByteArray(6)
            // data[0..3] => x1,y1,x2,y2  data[4] => conf  data[5] => class
            val x1b = data[0].toInt() and 0xFF
            val y1b = data[1].toInt() and 0xFF
            val x2b = data[2].toInt() and 0xFF
            val y2b = data[3].toInt() and 0xFF
            val confb = data[4].toInt() and 0xFF
            val cls = data[5].toInt() and 0xFF

            // Допустим, x1b..y2b в пикселях (0..640,0..480)
            // Или x1b..y2b = normalized [0..1] => x1f = x1b/255f * 640
            // Пример: считаем, что это пиксели:
            val x1 = x1b.toFloat()
            val y1 = y1b.toFloat()
            val x2 = x2b.toFloat()
            val y2 = y2b.toFloat()

            val conf = (confb / 255f) // 0..1

            if (conf > confThreshold) {
                prelim.add(
                    DetectionResult(
                        x1, y1, x2, y2, conf, cls
                    )
                )
            }
        }

        return nonMaxSuppression(prelim, iouThreshold)
    }

    override fun close() {
        tflite?.close()
        tflite = null
    }

    private fun nonMaxSuppression(
        boxes: List<DetectionResult>,
        iouThreshold: Float
    ): List<DetectionResult> {
        val sorted = boxes.sortedByDescending { it.confidence }
        val selected = mutableListOf<DetectionResult>()

        for (candidate in sorted) {
            var keep = true
            for (existing in selected) {
                if (candidate.classId == existing.classId) {
                    val iouVal = iou(candidate, existing)
                    if (iouVal > iouThreshold) {
                        keep = false
                        break
                    }
                }
            }
            if (keep) selected.add(candidate)
        }
        return selected
    }

    private fun iou(a: DetectionResult, b: DetectionResult): Float {
        val inter = intersection(a, b)
        val union = area(a) + area(b) - inter
        return if (union <= 0f) 0f else inter / union
    }

    private fun intersection(a: DetectionResult, b: DetectionResult): Float {
        val x1 = max(a.x1, b.x1)
        val y1 = max(a.y1, b.y1)
        val x2 = min(a.x2, b.x2)
        val y2 = min(a.y2, b.y2)
        val w = x2 - x1
        val h = y2 - y1
        return if (w <= 0f || h <= 0f) 0f else w * h
    }

    private fun area(r: DetectionResult): Float {
        val w = r.x2 - r.x1
        val h = r.y2 - r.y1
        return if (w < 0f || h < 0f) 0f else w * h
    }
}