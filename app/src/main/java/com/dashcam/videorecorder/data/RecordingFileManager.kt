package com.dashcam.videorecorder.data

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

object RecordingFileManager {

    private val recordedVideos = mutableListOf<RecordedVideo>()

    fun createVideoFile(context: Context): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        if (dir != null && !dir.exists()) {
            dir.mkdirs()
        }
        val timestamp = System.currentTimeMillis()

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val formattedDate = sdf.format(timestamp)
        val filename = "video_$formattedDate.mp4"
        return File(dir, filename)
    }

    fun addRecordedFile(file: File) {
        recordedVideos.add(
            RecordedVideo(
                file = file,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    fun checkAndCleanup(context: Context, maxMemoryMb: Int) {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir


        val allVideos = dir.listFiles { f -> f.extension.equals("mp4", ignoreCase = true) }
            ?.toList() ?: emptyList()

        var totalSizeMb = allVideos.sumOf { it.length() }.toDouble() / (1024.0 * 1024.0)
        if (totalSizeMb <= maxMemoryMb) return

        val sortedByDate = allVideos.sortedBy { it.lastModified() }
        for (oldFile in sortedByDate) {
            if (totalSizeMb <= maxMemoryMb) break
            val sizeMb = oldFile.length().toDouble() / (1024.0 * 1024.0)
            oldFile.delete()
            Log.d("cycleClean","Файл ${oldFile.name} удален!")
            totalSizeMb -= sizeMb
        }
    }

}


/**
 * Модель, описывающая записанное видео: файл + время завершения.
 */
data class RecordedVideo(
    val file: File,
    val timestamp: Long
)