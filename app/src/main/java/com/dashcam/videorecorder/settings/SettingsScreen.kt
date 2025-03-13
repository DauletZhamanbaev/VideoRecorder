package com.dashcam.videorecorder.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dashcam.videorecorder.settings.SettingsData
import com.dashcam.videorecorder.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
    onBack: () -> Unit
) {
    val settings by settingsViewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Video Resolution
            Text("Качество видео")
            var resolutionExpanded by remember { mutableStateOf(false) }
            Box {
                Text(
                    text = settings.videoResolution,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { resolutionExpanded = true }
                        .padding(16.dp)
                )
                DropdownMenu(expanded = resolutionExpanded, onDismissRequest = { resolutionExpanded = false }) {
                    listOf("FHD", "HD", "HIGHEST").forEach { res ->
                        DropdownMenuItem(
                            text = { Text(res) },
                            onClick = {
                                resolutionExpanded = false
                                settingsViewModel.updateSettings(settings.copy(videoResolution = res))
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bitrate
            Text("Битрейт: ${settings.bitrate}")
            Slider(
                value = settings.bitrate.toFloat(),
                onValueChange = { newValue ->
                    settingsViewModel.updateSettings(settings.copy(bitrate = newValue.toInt()))
                },
                valueRange = 1_000_000f..10_000_000f
            )

            Spacer(modifier = Modifier.height(16.dp))

            // FPS
            Text("FPS: ${settings.fps}")
            Slider(
                value = settings.fps.toFloat(),
                onValueChange = { newValue ->
                    settingsViewModel.updateSettings(settings.copy(fps = newValue.toInt()))
                },
                valueRange = 15f..60f
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Maximum Memory
            Text("Память (MB): ${settings.maxMemory}")
            Slider(
                value = settings.maxMemory.toFloat(),
                onValueChange = { newValue ->
                    settingsViewModel.updateSettings(settings.copy(maxMemory = newValue.toInt()))
                },
                valueRange = 100f..1000f
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Circular Recording
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Циклическая запись")
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = settings.circularRecording,
                    onCheckedChange = {
                        settingsViewModel.updateSettings(settings.copy(circularRecording = it))
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Max Duration
            Text("Максимальная длительность (sec): ${settings.maxDuration}")
            Slider(
                value = settings.maxDuration.toFloat(),
                onValueChange = { newValue ->
                    settingsViewModel.updateSettings(settings.copy(maxDuration = newValue.toInt()))
                },
                valueRange = 10f..300f
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Road Sign Recognition
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Детектирование знаков")
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = settings.roadSignRecognition,
                    onCheckedChange = {
                        settingsViewModel.updateSettings(settings.copy(roadSignRecognition = it))
                    }
                )
            }
        }
    }
}