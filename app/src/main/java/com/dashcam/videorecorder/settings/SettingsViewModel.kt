package com.dashcam.videorecorder.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dashcam.videorecorder.settings.SettingsData
import com.dashcam.videorecorder.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application.applicationContext)

    val settings: StateFlow<SettingsData> = repository.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SettingsData("FHD", 4000000, 30, 500, false, 60, true)
    )

    fun updateSettings(newSettings: SettingsData) {
        viewModelScope.launch {
            repository.updateSettings(newSettings)
        }
    }
}