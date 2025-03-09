package com.dashcam.videorecorder.data

import android.content.Context
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale


object PhotoFileManager {
    fun createPhotoFile(context: Context): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        if (dir != null && !dir.exists()) {
            dir.mkdirs()
        }
        val timestamp = System.currentTimeMillis()
        val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val formattedDate = sdf.format(timestamp)
        val filename = "photo_$formattedDate.jpg"
        return File(dir, filename)
    }
}