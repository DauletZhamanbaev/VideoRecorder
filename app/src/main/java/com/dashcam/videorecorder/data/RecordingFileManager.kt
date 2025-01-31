package com.dashcam.videorecorder.data

import android.content.Context
import android.os.Environment
import java.io.File

object RecordingFileManager {

    fun createVideoFile(context: Context): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        if (dir != null && !dir.exists()) {
            dir.mkdirs()
        }
        val timestamp = System.currentTimeMillis()
        val filename = "video_$timestamp.mp4"
        return File(dir, filename)
    }

}