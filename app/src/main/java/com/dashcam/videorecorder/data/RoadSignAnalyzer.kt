package com.dashcam.videorecorder.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.os.Environment
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.ui.platform.LocalContext
import com.dashcam.videorecorder.model.DetectionResult
import com.dashcam.videorecorder.model.ModelInterface
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
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
    private val classifier: RoadSignClassifier,
    private val  context: Context,
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
                    Log.d("RoadSignAnalyzer", "Detected box=$det")
                    val classId = classifySign(rgbBuffer, image.width, image.height, det)
                    Log.d("RoadSignAnalyzer", "classId=$classId")
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

    private fun classifySign(
        rgbBuffer: ByteBuffer,
        srcWidth: Int,
        srcHeight: Int,
        det: DetectionResult
    ): Int {
        val x1 = det.x1.toInt().coerceAtLeast(0)
        val y1 = det.y1.toInt().coerceAtLeast(0)
        val x2 = det.x2.toInt().coerceAtMost(srcWidth)
        val y2 = det.y2.toInt().coerceAtMost(srcHeight)

        val w = (x2 - x1).coerceAtLeast(1)
        val h = (y2 - y1).coerceAtLeast(1)

        Log.d("RoadSignAnalyzer", "classifySign: (x1=$x1,y1=$y1,x2=$x2,y2=$y2) => w=$w,h=$h")


        // 1) Вызываем crop+resize => ByteBuffer(32x32x3)
        val roi = cropAndResizeTo32x32(rgbBuffer, srcWidth, srcHeight, x1, y1, w, h)
        // 2) Скармливаем классификатору
        return classifier.classifyROI(roi)
    }

    private fun cropAndResizeTo32x32(
        rgbBuffer: ByteBuffer,
        srcWidth: Int,
        srcHeight: Int,
        x: Int, y: Int,
        w: Int, h: Int

    ): ByteBuffer {
        val outW = 32
        val outH = 32

        Log.d("cropAndResizeTo32x32","IN: x=$x, y=$y, w=$w, h=$h, srcWidth=$srcWidth, srcHeight=$srcHeight")

        // 1) Читаем исходный rgbBuffer => ByteArray
        val srcData = ByteArray(srcWidth * srcHeight * 3)
        rgbBuffer.rewind()
        rgbBuffer.get(srcData)

        // 2) Создаём ByteArray(32*32*3)
        val dstData = ByteArray(outW * outH * 3)

        // 3) nearest neighbor (упрощённо)
        for (row in 0 until outH) {
            val srcY = y + (row * h) / outH
            for (col in 0 until outW) {
                val srcX = x + (col * w) / outW
                val srcIndex = (srcY * srcWidth + srcX) * 3
                val dstIndex = (row * outW + col) * 3
                dstData[dstIndex]   = srcData[srcIndex]
                dstData[dstIndex+1] = srcData[srcIndex+1]
                dstData[dstIndex+2] = srcData[srcIndex+2]
            }
        }

        debugSaveROI(context, outW, outH, dstData)


        // 4) Кладём в ByteBuffer
        val roiBuf = ByteBuffer.allocateDirect(dstData.size).order(ByteOrder.nativeOrder())
        roiBuf.put(dstData)
        roiBuf.rewind()
        return roiBuf
    }

    private fun debugSaveROI(
        context: Context,
        outW: Int,
        outH: Int,
        rgbData: ByteArray // 3-канальный [0..255] массив размером outW*outH*3
    ) {
        // 1) Создаём Bitmap (ARGB_8888) и заполняем пиксели
        val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)

        // Индекс в rgbData
        var index = 0
        for (row in 0 until outH) {
            for (col in 0 until outW) {
                val r = (rgbData[index].toInt() and 0xFF)
                val g = (rgbData[index+1].toInt() and 0xFF)
                val b = (rgbData[index+2].toInt() and 0xFF)
                index += 3

                // ARGB: alpha=255
                val color = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                bmp.setPixel(col, row, color)
            }
        }

        // 2) Сохраняем BMP во временный файл
        val filename = "roi_debug_${System.currentTimeMillis()}.png"
//        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), filename)
//        FileOutputStream(file).use { fos ->
//            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
//        }
//        Log.d("debugSaveROI", "ROI saved to ${file.absolutePath}")
    }





    private fun opencvResizeRGBBuffer(
        originalBuffer: ByteBuffer,
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int
    ): ByteBuffer {
        originalBuffer.rewind()
        val srcData = ByteArray(srcWidth * srcHeight * 3)
        originalBuffer.get(srcData)


        val srcMat = Mat(srcHeight, srcWidth, CvType.CV_8UC3)
        srcMat.put(0, 0, srcData)

        val dstMat = Mat(dstHeight, dstWidth, CvType.CV_8UC3)

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
}
