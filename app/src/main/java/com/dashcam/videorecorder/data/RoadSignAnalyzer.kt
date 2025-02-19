package com.dashcam.videorecorder.data

import android.graphics.ImageFormat
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.dashcam.videorecorder.model.DetectionResult
import com.dashcam.videorecorder.model.ModelInterface
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * RoadSignAnalyzer — это класс ImageAnalysis.Analyzer, получающий кадры от CameraX.
 * Он конвертирует кадр в формат, который ожидает модель, и вызывает model.runInference(...).
 * Результаты распознавания передаются через колбэк onDetections.
 *
 * В данном варианте учтено, что у модели могут быть заданные ширина/высота (inputWidth/inputHeight).
 * Если разрешение с камеры другое, мы делаем resize при необходимости.
 */
class RoadSignAnalyzer(
    private val model: ModelInterface,
    private val onDetections: (List<DetectionResult>) -> Unit
) : ImageAnalysis.Analyzer {

    override fun analyze(image: ImageProxy) {
        try {
            if (image.format != ImageFormat.YUV_420_888) {
                image.close()
                return
            }

            // Модель ожидает (640×480)
            val modelWidth = 640
            val modelHeight = 480

            val rotation = image.imageInfo.rotationDegrees
            Log.d("RoadSignAnalyzer","Analyze: cameraWidth=${image.width}, cameraHeight=${image.height}, rotation=$rotation")


            val cameraWidth = image.width  // обычно 1280
            val cameraHeight = image.height // обычно 960, etc.


            // 1) YUV -> RGB
            val rgbBuffer = yuvToRGBByteBuffer(image)

            // 2) Если размер != (640×480), делаем resize
            val inputForModel = if (cameraWidth == modelWidth && cameraHeight == modelHeight) {
                rgbBuffer
            } else {
                opencvResizeRGBBuffer(rgbBuffer, image.width, image.height, modelWidth,modelHeight)
            }

            Log.d("RoadSignAnalyzer","Analyze: cameraWidth=${image.width}, cameraHeight=${image.height}, rotation=$rotation")

            Log.d("RoadSignAnalyzer","Resizing from ($cameraWidth x $cameraHeight) to (640x480)")

            // Вызываем инференс
            val detections = model.runInference(inputForModel)
            Log.d("RoadSignAnalyzer","Detected: ${detections.size}")

            if (detections.isNotEmpty()) {
                Log.d("RoadSignAnalyzer", "Найдено детекций: ${detections.size}")
                detections.forEach { det ->
                    Log.d("RoadSignAnalyzer", "  -> $det")
                }
            }

            onDetections(detections)
        } finally {
            image.close()
        }
    }

    // Примитивная конвертация YUV->RGB
    private fun yuvToRGBByteBuffer(image: ImageProxy): ByteBuffer {
        val w = image.width
        val h = image.height
        val outBuffer = ByteBuffer.allocateDirect(w * h * 3)
        outBuffer.order(ByteOrder.nativeOrder())

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        for (row in 0 until h) {
            val yRowOffset = row * yRowStride
            val uvRowOffsetU = (row shr 1) * uRowStride
            val uvRowOffsetV = (row shr 1) * vRowStride

            for (col in 0 until w) {
                val yIndex = yRowOffset + col * yPixelStride
                val Y = (yBuffer.get(yIndex).toInt() and 0xFF)

                val uvCol = (col shr 1)
                val uIndex = uvRowOffsetU + uvCol * uPixelStride
                val vIndex = uvRowOffsetV + uvCol * vPixelStride

                val U = (uBuffer.get(uIndex).toInt() and 0xFF) - 128
                val V = (vBuffer.get(vIndex).toInt() and 0xFF) - 128

                // BT.601
                val Yf = (Y - 16).coerceAtLeast(0).toFloat()
                val rF = 1.164f * Yf + 1.596f * V
                val gF = 1.164f * Yf - 0.391f * U - 0.813f * V
                val bF = 1.164f * Yf + 2.018f * U

                val r = rF.toInt().coerceIn(0, 255).toByte()
                val g = gF.toInt().coerceIn(0, 255).toByte()
                val b = bF.toInt().coerceIn(0, 255).toByte()

                outBuffer.put(r)
                outBuffer.put(g)
                outBuffer.put(b)
            }
        }


        outBuffer.rewind()
        return outBuffer
    }

    private fun resizeRGBBuffer(
        originalBuffer: ByteBuffer,
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int
    ): ByteBuffer {
        val dstBufferSize = dstWidth * dstHeight * 3
        val dstBuffer = ByteBuffer.allocateDirect(dstBufferSize).order(ByteOrder.nativeOrder())

        originalBuffer.rewind()
        val srcData = ByteArray(srcWidth * srcHeight * 3)
        originalBuffer.get(srcData)

        val xRatio = srcWidth.toFloat() / dstWidth
        val yRatio = srcHeight.toFloat() / dstHeight

        for (row in 0 until dstHeight) {
            val srcY = (row * yRatio).toInt()
            for (col in 0 until dstWidth) {
                val srcX = (col * xRatio).toInt()
                val srcIndex = (srcY * srcWidth + srcX) * 3

                val r = srcData[srcIndex]
                val g = srcData[srcIndex + 1]
                val b = srcData[srcIndex + 2]

                dstBuffer.put(r)
                dstBuffer.put(g)
                dstBuffer.put(b)
            }
        }

        dstBuffer.rewind()
        return dstBuffer
    }


    private fun opencvResizeRGBBuffer(
        originalBuffer: ByteBuffer,
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int
    ): ByteBuffer {
        // 1) Прочитаем исходные байты
        originalBuffer.rewind()
        val srcData = ByteArray(srcWidth * srcHeight * 3)
        originalBuffer.get(srcData)


        val srcMat = Mat(srcHeight, srcWidth, CvType.CV_8UC3)
        srcMat.put(0, 0, srcData)

        // 3) Создаём Mat для результата
        val dstMat = Mat(dstHeight, dstWidth, CvType.CV_8UC3)

        // 4) Выполняем биллинейный ресайз
        Imgproc.resize(
            srcMat,
            dstMat,
            Size(dstWidth.toDouble(), dstHeight.toDouble()),
            0.0,
            0.0,
            Imgproc.INTER_LINEAR
        )

        val dstData = ByteArray(dstWidth * dstHeight * 3)
        dstMat.get(0, 0, dstData)

        val outBuffer = ByteBuffer.allocateDirect(dstData.size)
        outBuffer.order(ByteOrder.nativeOrder())
        outBuffer.put(dstData)
        outBuffer.rewind()

        srcMat.release()
        dstMat.release()

        return outBuffer
    }
}
