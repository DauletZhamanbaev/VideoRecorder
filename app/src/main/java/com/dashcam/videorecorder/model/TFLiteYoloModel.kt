package com.dashcam.videorecorder.model

import android.content.Context
import android.util.Log
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

        val rawOutput = Array(1) {
            Array(18900) {
                ByteArray(6)
            }
        }
        Log.d("TfLiteYoloModel","runInference: inputData remaining=${inputData.remaining()} (should be 921600 if 640x480x3)")


        tflite!!.run(inputData, rawOutput)

        val results = mutableListOf<DetectionResult>()
        val arr = rawOutput[0]

        val baseWidth = 640f
        val baseHeight= 480f
        val rotation = 90

        for (i in arr.indices) {
            val data = arr[i]
            // data = [cxB, cyB, wB, hB, confB, clsB], quant8
            val cxB = data[0].toInt() and 0xFF
            val cyB = data[1].toInt() and 0xFF
            val wB = data[2].toInt() and 0xFF
            val hB = data[3].toInt() and 0xFF
            val confB = data[4].toInt() and 0xFF
            val cls = data[5].toInt() and 0xFF

            val conf = confB / 255f
            if (conf > 0.5f) {
                val cxNorm = cxB / 255f
                val cyNorm = cyB / 255f
                val wNorm = wB / 255f
                val hNorm = hB / 255f

                // модель logic => [cx,cy,w,h] in [0..1] => scale up
                val cx = cxNorm * baseWidth
                val cy = cyNorm * baseHeight
                val wPix = wNorm * baseWidth
                val hPix = hNorm * baseHeight

                var x1 = cx - wPix / 2
                var x2 = cx + wPix / 2
                var y1 = cy - hPix / 2
                var y2 = cy + hPix / 2
                Log.d("TfLiteYoloModel","Before rotate: x1=$x1,y1=$y1, x2=$x2,y2=$y2, rotation=$rotation")

                // Учитываем rotation
                if (rotation == 90) {
                    // rotate +90 around (0,0)
                    val (rx1, ry1) = rotate90(x1, y1, baseWidth, baseHeight)
                    val (rx2, ry2) = rotate90(x2, y2, baseWidth, baseHeight)
                    x1 = min(rx1, rx2)
                    x2 = max(rx1, rx2)
                    y1 = min(ry1, ry2)
                    y2 = max(ry1, ry2)
                }
                Log.d("TfLiteYoloModel","After rotate: x1=$x1,y1=$y1, x2=$x2,y2=$y2")

                // if rotation=270 => rotate270, etc.

                results.add(DetectionResult(x1, y1, x2, y2, conf, cls))
            }
        }

        return nonMaxSuppression(results, iouThreshold)
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
    private fun rotate90(x: Float, y: Float, w: Float, h: Float): Pair<Float,Float> {
        // +90 => (x,y)->(y, w-x)
        return Pair(y, w - x)
    }
}