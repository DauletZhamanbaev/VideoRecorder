package com.dashcam.videorecorder.data

import android.content.Context
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

object RecordingFileManager {

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

}