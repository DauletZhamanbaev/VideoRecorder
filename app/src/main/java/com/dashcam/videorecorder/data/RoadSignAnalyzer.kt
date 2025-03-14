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
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
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
    private val onDetections: (List<DetectionResult>) -> Unit,
    private  val onClassification: (Int) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        private var debugRoiCount = 0
        private const val MAX_DEBUG_ROI = 0
        private var debugImageCount = 0
        private const val MAX_DEBUG_IMAGE = 0
    }

    override fun analyze(image: ImageProxy) {
        try {
            if (image.format != ImageFormat.YUV_420_888) {
                image.close()
                return
            }

            val modelWidth = 640
            val modelHeight = 480

            val rotation = 0
            //val rotation = image.imageInfo.rotationDegrees
            Log.d("RoadSignAnalyzer","Analyze: cameraWidth=${image.width}, cameraHeight=${image.height}, rotation=$rotation")


            val cameraWidth = image.width
            val cameraHeight = image.height


            // 1) YUV -> RGB
            val rgbBuffer = yuvToRGBByteBuffer(image)

            //Поворот
            val rgbData = ByteArray(cameraWidth * cameraHeight * 3)
            rgbBuffer.rewind()
            rgbBuffer.get(rgbData)
            Log.d("RoadSignAnalyzer","[analyze] rgbData.size=${rgbData.size}")
            // Создаем Mat: размеры = (cameraHeight x cameraWidth)
            val srcMat = Mat(cameraHeight, cameraWidth, CvType.CV_8UC3)
            srcMat.put(0, 0, rgbData)
            Log.d("RoadSignAnalyzer","[analyze] srcMat size=(${srcMat.cols()} x ${srcMat.rows()})")
            val rotatedMat = Mat()
            when (rotation) {
                0 -> srcMat.copyTo(rotatedMat)
                90 -> Core.rotate(srcMat, rotatedMat, Core.ROTATE_90_COUNTERCLOCKWISE)
                180 -> Core.rotate(srcMat, rotatedMat, Core.ROTATE_180)
                270 -> Core.rotate(srcMat, rotatedMat, Core.ROTATE_90_CLOCKWISE)
                else -> srcMat.copyTo(rotatedMat)
            }
            srcMat.release()

            val rotatedWidth = rotatedMat.cols()  // cols() = ширина
            val rotatedHeight = rotatedMat.rows() // rows() = высота
            Log.d("RoadSignAnalyzer", "After rotation: width=$rotatedWidth, height=$rotatedHeight")

            val resizedMat = cropTo4by3AndResize(rotatedMat, 640, 480)
            rotatedMat.release()
            Log.d("RoadSignAnalyzer","[analyze] resizedMat size=(${resizedMat.cols()} x ${resizedMat.rows()})")

            val finalData = ByteArray(modelWidth * modelHeight * 3)
            resizedMat.get(0, 0, finalData)
            resizedMat.release()

            Log.d("RoadSignAnalyzer","[analyze] finalData size=(${finalData.size}})")


            val inputForModel = ByteBuffer.allocateDirect(finalData.size).order(ByteOrder.nativeOrder())
            inputForModel.put(finalData)
            inputForModel.rewind()

            // 2) Если размер != (640×480), делаем resize
//            val inputForModel = if (cameraWidth == modelWidth && cameraHeight == modelHeight) {
//                rgbBuffer
//            } else {
//                opencvResizeRGBBuffer(rgbBuffer, image.width, image.height, modelWidth,modelHeight)
//            }
//
//            Log.d("RoadSignAnalyzer","Analyze: cameraWidth=${image.width}, cameraHeight=${image.height}, rotation=$rotation")
//
//            Log.d("RoadSignAnalyzer","Resizing from ($cameraWidth x $cameraHeight) to (640x480)")
//
//            val srcData = ByteArray(modelWidth * modelHeight * 3)
//            inputForModel.rewind()
//            inputForModel.get(srcData)
//            inputForModel.rewind()

            debugSaveImage(context, finalData, 640, 480)



            val x1 = 0
            val y1 = 0
            val cutWidth = 100
            val cutHeight = 500

            val x2 = (x1 + cutWidth).coerceAtMost(640)  // на случай выхода за границы
            val y2 = (y1 + cutHeight).coerceAtMost(480)

            //val cropData = cropRGB(srcData, 640, 480, x1, y1, x2, y2)

            //debugSaveImage(context, cropData, (x2 - x1), (y2 - y1))

            // Вызываем инференс
            val detections = model.runInference(inputForModel)
            Log.d("RoadSignAnalyzer","Detected: ${detections.size}")

            if (detections.isNotEmpty()) {
                Log.d("RoadSignAnalyzer", "Найдено детекций: ${detections.size}")
                detections.forEach { det ->
                    Log.d("RoadSignAnalyzer", "Detected box=$det")

                    val classId = classifySign(finalData , modelWidth, modelHeight, det)
                    Log.d("RoadSignAnalyzer", "classId=$classId")

                    if (classId >= 0) {
                        onClassification(classId)
                    }
                }
            }

            onDetections(detections)
        } finally {
            image.close()
        }
    }

    fun cropTo4by3AndResize(
        srcMat: Mat,
        outWidth: Int = 640,
        outHeight: Int = 480
    ): Mat {
        // srcMat: 1944×1944 (CV_8UC3)

        val srcW = srcMat.cols() // 1944
        val srcH = srcMat.rows() // 1944
        val targetRatio = 4.0/3.0

        // compute newH
        val newH = (srcW / targetRatio).toInt()  // = 1458
        // crop (srcW x newH) из (srcW x srcH)
        val yOffset = (srcH - newH)/2 // если хотим по центру, = (1944-1458)/2=243
        // cropRect = Rect(x=0, y=yOffset, width=srcW, height=newH)
        val cropRect = Rect(0, yOffset, srcW, newH)
        val croppedMat = Mat(srcMat, cropRect) // обрезка

        // Теперь у нас 1944×1458. Масштабируем до 640×480
        val resizedMat = Mat()
        Imgproc.resize(
            croppedMat,
            resizedMat,
            Size(outWidth.toDouble(), outHeight.toDouble()),
            0.0,
            0.0,
            Imgproc.INTER_LINEAR
        )
        croppedMat.release()

        return resizedMat
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
        srcData: ByteArray,
        srcWidth: Int,
        srcHeight: Int,
        det: DetectionResult
    ): Int {

        Log.d("ROI_Debug", "Original det: $det")
        val xMin = 0f
        val xMax = 440f
        val yMin = 25f
        val yMax = 310f

        val (nx1, ny1, nx2, ny2) = transformBbox(
            det.x1, det.y1, det.x2, det.y2,
            xMin, xMax, yMin, yMax,
            targetW = 640f,
            targetH = 480f
        )

        var x1 = nx1.toInt().coerceIn(0, srcWidth - 1)
        var y1 = ny1.toInt().coerceIn(0, srcHeight - 1)
        var x2 = nx2.toInt().coerceIn(0, srcWidth - 1)
        var y2 = ny2.toInt().coerceIn(0, srcHeight - 1)

        if (x2 < x1) {
            val temp = x1
            x1 = x2
            x2 = temp
        }
        if (y2 < y1) {
            val temp = y1
            y1 = y2
            y2 = temp
        }
        Log.d("ROI_Debug", "Adjusted ROI: x1=$x1, y1=$y1, x2=$x2, y2=$y2")

        val w = (x2 - x1).coerceAtLeast(1)
        val h = (y2 - y1).coerceAtLeast(1)

        Log.d("RoadSignAnalyzer", "classifySign: (x1=$x1,y1=$y1,x2=$x2,y2=$y2) => w=$w,h=$h")



        val detBeforeResize = cropRGB(srcData, 640, 480, x1, y1,x2,y2)

        debugSaveImage(context, detBeforeResize, w, h)

        // 1) Вызываем crop+resize => ByteBuffer(32x32x3)
        val roiData = bilinearResizeROI(srcData, srcWidth, srcHeight, x1, y1, w, h,32,32)
        debugSaveROI(context, 32, 32, roiData)



        val roiBuffer = ByteBuffer.allocateDirect(roiData.size).order(ByteOrder.nativeOrder())
        roiBuffer.put(roiData)
        roiBuffer.rewind()

        // 2) Скармливаем классификатору
        return classifier.classifyROI(roiBuffer)
    }

    fun transformBbox(
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        xMin: Float, xMax: Float,
        yMin: Float, yMax: Float,
        targetW: Float,
        targetH: Float
    ): Quad<Float,Float,Float,Float> {

        // Простой линейный scale по X (без инверсии)
        fun scaleX(x: Float): Float {
            // если xMin=0, xMax=440, то x' = (x/440)*640
            return ((x - xMin)/(xMax - xMin)) * targetW
        }
        // Простой линейный scale по Y (без инверсии)
        fun scaleY(y: Float): Float {
            // если yMin=25, yMax=310, то y' = ((y-25)/(310-25))*480
            return ((y - yMin)/(yMax - yMin)) * targetH
        }

        // Преобразуем углы
        val nx1 = scaleX(x1)
        val nx2 = scaleX(x2)
        val ny1 = scaleY(y1)
        val ny2 = scaleY(y2)

        return Quad(nx1, ny1, nx2, ny2)
    }

    data class Quad<A,B,C,D>(val first: A, val second: B, val third: C, val fourth: D)


    private fun adjustCoordinatesForRotation(x: Float, y: Float, imgWidth: Int, imgHeight: Int, rotation: Int): Pair<Float, Float> {
        return when (rotation) {
            90 -> Pair(y, (imgWidth - 1) - x)
            270 -> Pair((imgHeight - 1) - y, x)
            180 -> Pair((imgWidth - 1) - x, (imgHeight - 1) - y)
            else -> Pair(x, y)
        }
    }

    fun cropRGB(
        srcData: ByteArray,
        srcWidth: Int,
        srcHeight: Int,
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int
    ): ByteArray {

        if (srcData.isEmpty()) {
            Log.e("DebugImage", "Empty cropData, skip saving image")
            return ByteArray(0)
        }
        // Убедимся, что координаты в пределах
        if (x1 < 0 || y1 < 0 || x2 > srcWidth || y2 > srcHeight || x2 <= x1 || y2 <= y1) {
            // Можно кинуть исключение или вернуть пустой массив
            return ByteArray(0)
        }

        val cropW = x2 - x1
        val cropH = y2 - y1
        val dstData = ByteArray(cropW * cropH * 3)

        var dstIndex = 0
        for (row in 0 until cropH) {
            val srcY = y1 + row
            for (col in 0 until cropW) {
                val srcX = x1 + col

                val srcIndex = (srcY * srcWidth + srcX) * 3
                // Копируем R,G,B
                dstData[dstIndex]     = srcData[srcIndex]
                dstData[dstIndex + 1] = srcData[srcIndex + 1]
                dstData[dstIndex + 2] = srcData[srcIndex + 2]
                dstIndex += 3
            }
        }
        return dstData
    }

    private fun cropAndResizeTo32x32(
        srcData: ByteArray,
        srcWidth: Int,
        srcHeight: Int,
        x: Int, y: Int,
        w: Int, h: Int

    ): ByteBuffer {
        val outW = 32
        val outH = 32
        Log.d("cropAndResizeTo32x32", "IN: x=$x, y=$y, w=$w, h=$h, srcWidth=$srcWidth, srcHeight=$srcHeight")
        val dstData = ByteArray(outW * outH * 3)

        // Реализация масштабирования методом nearest neighbor
        for (row in 0 until outH) {
            val srcY = y + (row * h) / outH
            for (col in 0 until outW) {
                val srcX = x + (col * w) / outW
                val srcIndex = (srcY * srcWidth + srcX) * 3
                val dstIndex = (row * outW + col) * 3

                if (srcX in 0 until srcWidth && srcY in 0 until srcHeight && srcIndex + 2 < srcData.size) {
                    dstData[dstIndex]     = srcData[srcIndex]
                    dstData[dstIndex + 1] = srcData[srcIndex + 1]
                    dstData[dstIndex + 2] = srcData[srcIndex + 2]
                } else {
                    Log.e("cropAndResizeTo32x32", "Invalid source coordinates: srcX=$srcX, srcY=$srcY")
                    return ByteBuffer.allocate(0)
                }
            }
        }

        val roiBuf = ByteBuffer.allocateDirect(dstData.size).order(ByteOrder.nativeOrder())
        roiBuf.put(dstData)
        roiBuf.rewind()

        if (roiBuf.remaining() == 0) {
            Log.e("cropAndResizeTo32x32", "Processed buffer is empty.")
            return ByteBuffer.allocate(0)
        }
        return roiBuf
    }

    fun bilinearResizeROI(
        srcData: ByteArray,
        srcW: Int,
        srcH: Int,
        roiX: Int,
        roiY: Int,
        roiW: Int,
        roiH: Int,
        outW: Int,
        outH: Int
    ): ByteArray {
        // Массив для итогового результата
        val dstData = ByteArray(outW * outH * 3)

        // Коэффициенты для пересчёта координат
        val xRatio = roiW.toFloat() / outW
        val yRatio = roiH.toFloat() / outH

        for (j in 0 until outH) {
            val syF = j * yRatio
            val sy = syF.toInt().coerceIn(0, roiH - 1)
            val v = syF - sy

            for (i in 0 until outW) {
                val sxF = i * xRatio
                val sx = sxF.toInt().coerceIn(0, roiW - 1)
                val u = sxF - sx

                // Индексы четырёх ближайших пикселей: (sx, sy), (sx+1, sy), (sx, sy+1), (sx+1, sy+1)
                // с учётом roiX, roiY смещаемся в srcData
                val c00 = getPixel(srcData, srcW, srcH, roiX + sx,     roiY + sy)
                val c10 = getPixel(srcData, srcW, srcH, roiX + sx + 1, roiY + sy)
                val c01 = getPixel(srcData, srcW, srcH, roiX + sx,     roiY + sy + 1)
                val c11 = getPixel(srcData, srcW, srcH, roiX + sx + 1, roiY + sy + 1)

                // Линейная интерполяция по x
                val c0 = lerp(c00, c10, u)
                val c1 = lerp(c01, c11, u)

                // Линейная интерполяция по y
                val c = lerp(c0, c1, v)

                // Записываем итог в dstData
                val dstIndex = (j * outW + i) * 3
                dstData[dstIndex    ] = c[0].toByte()
                dstData[dstIndex + 1] = c[1].toByte()
                dstData[dstIndex + 2] = c[2].toByte()
            }
        }
        return dstData
    }

    // Получение пикселя (R,G,B) из srcData (учитывая выход за границы)
    private fun getPixel(srcData: ByteArray, srcW: Int, srcH: Int, x: Int, y: Int): IntArray {
        if (x < 0 || x >= srcW || y < 0 || y >= srcH) {
            return intArrayOf(0, 0, 0) // За пределами => чёрный
        }
        val index = (y * srcW + x) * 3
        return intArrayOf(
            srcData[index].toInt() and 0xFF,
            srcData[index + 1].toInt() and 0xFF,
            srcData[index + 2].toInt() and 0xFF
        )
    }

    // Линейная интерполяция двух цветовых векторов (RGB)
    private fun lerp(c1: IntArray, c2: IntArray, t: Float): IntArray {
        val r = c1[0] + (c2[0] - c1[0]) * t
        val g = c1[1] + (c2[1] - c1[1]) * t
        val b = c1[2] + (c2[2] - c1[2]) * t
        return intArrayOf(r.toInt(), g.toInt(), b.toInt())
    }


    private fun debugSaveROI(
        context: Context,
        outW: Int,
        outH: Int,
        rgbData: ByteArray // 3-канальный [0..255] массив размером outW*outH*3
    ) {
        // 1) Создаём Bitmap (ARGB_8888) и заполняем пиксели
        if (debugRoiCount >= MAX_DEBUG_ROI) return
        debugRoiCount++

        // Создаём Bitmap формата ARGB_8888 и заполняем пиксели из rgbData
        val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        var index = 0
        for (row in 0 until outH) {
            for (col in 0 until outW) {
                val r = (rgbData[index].toInt() and 0xFF)
                val g = (rgbData[index + 1].toInt() and 0xFF)
                val b = (rgbData[index + 2].toInt() and 0xFF)
                index += 3

                // Формируем цвет: alpha=255
                val color = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                bmp.setPixel(col, row, color)
            }
        }

        // Сохраняем Bitmap во временный файл
        val filename = "roi_debug_${System.currentTimeMillis()}.png"
        try {
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), filename)
            FileOutputStream(file).use { fos ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            Log.d("debugSaveROI", "ROI saved to ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("debugSaveROI", "Error saving ROI: ${e.message}")
        }
    }

    fun debugSaveImage(context: Context, data: ByteArray, width: Int, height: Int) {
        if (debugImageCount >= MAX_DEBUG_IMAGE) return
        debugImageCount++

        // Создаём Bitmap
        // Здесь предполагается, что данные в формате RGB. Bitmap.createBitmap принимает ARGB, поэтому потребуется преобразование.
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        var index = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = data[index].toInt() and 0xFF
                val g = data[index + 1].toInt() and 0xFF
                val b = data[index + 2].toInt() and 0xFF
                index += 3
                val color = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                bmp.setPixel(x, y, color)
            }
        }
        val filename = "image_debug_${System.currentTimeMillis()}.png"
        // Сохраняем Bitmap как PNG
        try {
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), filename)
            FileOutputStream(file).use { fos ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            Log.d("DebugImage", "Image saved to ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("DebugImage", "Error saving image: ${e.message}")
        }
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
