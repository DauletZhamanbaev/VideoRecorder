package com.dashcam.videorecorder.camera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun TopLeftIconsRow(
    isLandscape: Boolean,
    onSwitchOrientation: () -> Unit,
    onOpenGallery: () -> Unit,
    onSwitchCamera: () -> Unit
) {
    // В ландшафтном режиме поворачиваем иконки на 90°,
    val iconModifier = if (isLandscape) Modifier.rotate(90f) else Modifier

    if (!isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp)
        ) {
            IconButton(onClick = { onSwitchOrientation() }) {
                Icon(
                    imageVector = Icons.Default.Repeat,
                    contentDescription = "Switch orientation",
                    tint = Color.White,
                    modifier = iconModifier
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            IconButton(onClick = { onSwitchCamera() }) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Switch camera",
                    tint = Color.White,
                    modifier = iconModifier
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            IconButton(onClick = {onOpenGallery()}) {
                Icon(
                    imageVector = Icons.Default.VideoLibrary,
                    contentDescription = "Library",
                    tint = Color.White,
                    modifier = iconModifier
                )
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = { onSwitchOrientation() }) {
                Icon(
                    imageVector = Icons.Default.Repeat,
                    contentDescription = "Switch orientation",
                    tint = Color.White,
                    modifier = iconModifier
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            IconButton(onClick = { onSwitchCamera() }) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Switch camera",
                    tint = Color.White,
                    modifier = iconModifier
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            IconButton(onClick = {onOpenGallery()}) {
                Icon(
                    imageVector = Icons.Default.VideoLibrary,
                    contentDescription = "Library",
                    tint = Color.White,
                    modifier = iconModifier
                )
            }
        }
    }
}