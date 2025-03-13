package com.dashcam.videorecorder.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.map

data class SettingsData(
    val videoResolution: String,
    val bitrate: Int,
    val fps: Int,
    val maxMemory: Int,
    val circularRecording: Boolean,
    val maxDuration: Int,
    val roadSignRecognition: Boolean
)

class SettingsRepository(private val context: Context) {

    val settingsFlow: Flow<SettingsData> = context.dataStore.data.map { preferences ->
        SettingsData(
            videoResolution = preferences[SettingsKeys.VIDEO_RESOLUTION] ?: "FHD",
            bitrate = preferences[SettingsKeys.BITRATE] ?: 4000000,
            fps = preferences[SettingsKeys.FPS] ?: 30,
            maxMemory = preferences[SettingsKeys.MAX_MEMORY] ?: 500,
            circularRecording = preferences[SettingsKeys.CIRCULAR_RECORDING] ?: false,
            maxDuration = preferences[SettingsKeys.MAX_DURATION] ?: 60,
            roadSignRecognition = preferences[SettingsKeys.ROAD_SIGN_RECOGNITION] ?: true
        )
    }

    suspend fun updateSettings(settings: SettingsData) {
        context.dataStore.edit { preferences ->
            preferences[SettingsKeys.VIDEO_RESOLUTION] = settings.videoResolution
            preferences[SettingsKeys.BITRATE] = settings.bitrate
            preferences[SettingsKeys.FPS] = settings.fps
            preferences[SettingsKeys.MAX_MEMORY] = settings.maxMemory
            preferences[SettingsKeys.CIRCULAR_RECORDING] = settings.circularRecording
            preferences[SettingsKeys.MAX_DURATION] = settings.maxDuration
            preferences[SettingsKeys.ROAD_SIGN_RECOGNITION] = settings.roadSignRecognition
        }
    }
}