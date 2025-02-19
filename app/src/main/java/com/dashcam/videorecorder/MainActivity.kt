package com.dashcam.videorecorder

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.dashcam.videorecorder.screens.MainScreen
import org.opencv.android.OpenCVLoader


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Cannot load OpenCV!")
        } else {
            Log.d("OpenCV", "OpenCV loaded successfully!")
        }
        setContent{
            VideoRecorderApp()
        }

    }
    companion object {
        private val CAMERAX_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
        )
    }
}

@Composable
fun VideoRecorderApp(){
    MaterialTheme{
        MainScreen()
    }
}