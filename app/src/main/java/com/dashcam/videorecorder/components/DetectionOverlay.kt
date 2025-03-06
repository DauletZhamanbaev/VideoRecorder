package com.dashcam.videorecorder.components

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.dashcam.videorecorder.model.DetectionResult
import com.dashcam.videorecorder.screens.Quad


fun transformBbox(
    x1: Float, y1: Float,
    x2: Float, y2: Float,
    xMin: Float, xMax: Float,
    yMin: Float, yMax: Float,
    targetW: Float,
    targetH: Float
): Quad<Float, Float, Float, Float> {

    val rx1 = minOf(x1,x2)
    val rx2 = maxOf(x1,x2)
    val ry1 = minOf(y1,y2)
    val ry2 = maxOf(y1,y2)

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
    val nx1 = scaleX(rx1)
    val nx2 = scaleX(rx2)
    val ny1 = scaleY(ry1)
    val ny2 = scaleY(ry2)

    Log.d("DETECTION_OVERLAY", "transformBbox: nx1=$nx1, ny1=$ny1, nx2=$nx2, ny2=$ny2")
    return Quad(nx1, ny1, nx2, ny2)
}


fun doSwapAndInvert(x: Float, y: Float, screenW: Float, screenH: Float): Pair<Float,Float> {
    // swap => newX= y, newY= x
    val newX = y
    val newY = x
    // invert X => finalX= screenH - newX, finalY= newY
    val fx = screenH - newX
    val fy = newY
    Log.d("DETECTION_OVERLAY", "doSwapAndInvert: x=$x, y=$y -> fx=$fx, fy=$fy")
    return Pair(fx, fy)
}

fun doSwapAndInvertY(x: Float, y: Float, screenW: Float, screenH: Float): Pair<Float,Float> {
    val newX = y
    val newY = x
    val fx = newX
    val fy = screenW - newY

    Log.d("DETECTION_OVERLAY", "doSwapAndInvertY: x=$x, y=$y -> fx=$fx, fy=$fy")
    return Pair(fx, fy)
}

fun rotateOrSwapIfNeeded(
    sx1: Float, sy1: Float,
    sx2: Float, sy2: Float,
    screenW: Float,
    screenH: Float,
    rotationDeg: Int
): Quad<Float, Float, Float, Float> {

    return when (rotationDeg) {
        0 -> {
            // без изменений
            Quad(sx1, sy1, sx2, sy2)
        }
        -90, 270 -> {
            // swapAxes + invert X => px = screenH - sy, py = sx
            // (т.е. мы "кладём" bbox на бок)
            val (fx1, fy1) = doSwapAndInvert(sx1, sy1, screenW, screenH)
            val (fx2, fy2) = doSwapAndInvert(sx2, sy2, screenW, screenH)
            Quad(fx1, fy1, fx2, fy2)
        }
        180 -> {
            // Повернуть на 180 => (x,y)->(screenW-x, screenH-y) -
            //   если хотим центральный поворот,
            //   можно (cx-x,cy-y).
            val fx1 = screenW - sx1
            val fy1 = screenH - sy1
            val fx2 = screenW - sx2
            val fy2 = screenH - sy2
            Quad(fx1, fy1, fx2, fy2)
        }
        90 -> {
            // 90 => swapAxes + invert Y?
            val (fx1, fy1) = doSwapAndInvertY(sx1, sy1, screenW, screenH)
            val (fx2, fy2) = doSwapAndInvertY(sx2, sy2, screenW, screenH)
            Quad(fx1, fy1, fx2, fy2)
        }
        else -> {
            // если хотим arbitrary angle,
            //   делаем rotateAroundCenter(...)
            Quad(sx1, sy1, sx2, sy2)
        }
    }
}

@Composable
fun DetectionOverlay(
    detectionList: List<DetectionResult>,
    modifier: Modifier = Modifier,
    screenRotationDeg: Int = -90
)
{
    if (detectionList.isEmpty()) return

    // Диапазоны координат детектора
    val xMin = 0f
    val xMax = 440f
    val yMin = 20f
    val yMax = 310f

    BoxWithConstraints(modifier = modifier) {

        val screenW: Float
        val screenH: Float

        // Здесь мы используем реальные размеры экрана для оверлея.
        if (screenRotationDeg in setOf(-90, 90, 270, -270)) {
            screenH = constraints.maxWidth.toFloat()
            screenW = constraints.maxHeight.toFloat()
        }
        else{
            screenH = constraints.maxHeight.toFloat()
            screenW = constraints.maxWidth.toFloat()
        }
        Log.d("DETECTION_OVERLAY", "Screen size: screenW=$screenW, screenH=$screenH")

        Canvas(modifier = Modifier.fillMaxSize()) {
            detectionList.forEach { det ->
                Log.d(
                    "DETECTION_OVERLAY",
                    "Detection coordinate: x1=${det.x1}, x2=${det.x2}, y1=${det.y1}, y2=${det.y2}"
                )
                // Преобразуем координаты из детекторного диапазона в экранное
                val (sx1, sy1, sx2, sy2) = transformBbox(
                    det.x1, det.y1, det.x2, det.y2,
                    xMin=xMin, xMax=xMax,
                    yMin=yMin, yMax=yMax,
                    targetW= screenW,
                    targetH= screenH
                )
                Log.d(
                    "DETECTION_OVERLAY",
                    "After transformBbox: sx1=$sx1, sy1=$sy1, sx2=$sx2, sy2=$sy2"
                )
                // Применяем поворот/свап, если требуется
                val (fx1, fy1, fx2, fy2) = rotateOrSwapIfNeeded(
                    sx1, sy1, sx2, sy2,
                    screenW, screenH,
                    screenRotationDeg
                )
                Log.d(
                    "DETECTION_OVERLAY",
                    "After rotate and swap: fx1=$fx1, fy1=$fy1, fx2=$fx2, fy2=$fy2"
                )
                // Берём финальные координаты
                var left = minOf(fx1, fx2)
                var right = maxOf(fx1, fx2)
                var top = minOf(fy1, fy2)
                var bottom = maxOf(fy1, fy2)

                val centerYbbox = (top + bottom)/2f
                val centerYscreen = screenW/2f
                val deltaY = centerYbbox - centerYscreen

                val alpha = 0.3f
                val correctionY = deltaY * alpha

                // Сдвигаем bbox
                top    -= correctionY
                bottom -= correctionY


                Log.d("DETECTION_OVERLAY", "final left - ${left}, finalRight - ${right}, finalTop -${top}, finalBottom - ${bottom}" )

                // Рисуем прямоугольник
                drawRect(
                    color = Color.Red.copy(alpha = 0.4f),
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }

}