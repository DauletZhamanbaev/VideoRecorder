package com.dashcam.videorecorder.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp




@Composable
fun BottomTransparentPanel(
    isLandscape: Boolean,
    onClickStartStop: () -> Unit,
    onClickSettings: () -> Unit,
    onClickPhoto: () -> Unit,
    isRecording: Boolean
) {
    if (!isLandscape) {
        // Портретная версия: панель внизу по центру
        Row(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(80.dp)
                .background(Color.Black.copy(alpha = 0.3f), shape = RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PanelButtons(
                onClickSettings = onClickSettings,
                onClickStartStop = onClickStartStop,
                onClickPhoto = onClickPhoto,
                isLandscape = isLandscape,
                isRecording = isRecording
            )
        }
    } else {
        // Ландшафтная версия: панель на левой стороне
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)  // Прижимаем колонку к левой стороне экрана
                    .fillMaxHeight(0.8f)
                    .width(80.dp)
                    .background(Color.Black.copy(alpha = 0.3f), shape = RoundedCornerShape(20.dp))
                    .padding(horizontal = 8.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PanelButtons(
                    onClickSettings = onClickSettings,
                    onClickStartStop = onClickStartStop,
                    onClickPhoto = onClickPhoto,
                    isRecording = isRecording,
                    isLandscape = isLandscape
                )
            }
        }
    }
}

@Composable
private fun PanelButtons(
    isLandscape: Boolean,
    onClickSettings: () -> Unit,
    onClickStartStop: () -> Unit,
    onClickPhoto: () -> Unit,
    isRecording: Boolean
) {
    IconButton(onClick = onClickSettings) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "Settings",
            tint = Color.White
        )
    }
    IconButton(onClick = onClickStartStop) {
        val icon = if (isRecording) Icons.Default.Stop else Icons.Default.Videocam
        Icon(
            imageVector = icon,
            contentDescription = "Start/Stop",
            tint = Color.White,
            modifier = Modifier.rotate(if (isLandscape) 90f else 0f)
        )
    }
    IconButton(onClick = onClickPhoto) {
        Icon(
            imageVector = Icons.Default.PhotoCamera,
            contentDescription = "Photo",
            tint = Color.White,
            modifier = Modifier.rotate(if (isLandscape) 90f else 0f)
        )
    }
}