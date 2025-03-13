package com.dashcam.videorecorder.settings


import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.dataStore by preferencesDataStore(name = "settings")

object SettingsKeys {
    val VIDEO_RESOLUTION = stringPreferencesKey("video_resolution")
    val BITRATE = intPreferencesKey("bitrate")
    val FPS = intPreferencesKey("fps")
    val MAX_MEMORY = intPreferencesKey("max_memory") // например, в мегабайтах
    val CIRCULAR_RECORDING = booleanPreferencesKey("circular_recording")
    val MAX_DURATION = intPreferencesKey("max_duration") // в секундах
    val ROAD_SIGN_RECOGNITION = booleanPreferencesKey("road_sign_recognition")
}