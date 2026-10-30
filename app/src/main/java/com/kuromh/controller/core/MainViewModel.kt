package com.kuromh.controller.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SaveStatus { IDLE, SUCCESS, FAILED }

private data class ModuleSnapshot(
    val rootGranted: Boolean,
    val moduleActive: Boolean = false,
    val version: String = MainViewModel.NOT_DETECTED,
    val configPath: String = MainViewModel.NOT_FOUND,
    val config: ModuleConfig = ModuleConfig()
)

class MainViewModel : ViewModel() {

    companion object {
        const val NOT_DETECTED = "Not Detected"
        const val NOT_FOUND = "Not Found"
    }

    // Apakah pengguna mengizinkan aplikasi memakai Root
    private val _isRootToggleEnabled = MutableStateFlow(false)
    val isRootToggleEnabled: StateFlow<Boolean> = _isRootToggleEnabled.asStateFlow()

    private val _isRootGranted = MutableStateFlow(false)
    val isRootGranted: StateFlow<Boolean> = _isRootGranted.asStateFlow()

    private val _isModuleActive = MutableStateFlow(false)
    val isModuleActive: StateFlow<Boolean> = _isModuleActive.asStateFlow()

    private val _moduleVersion = MutableStateFlow(NOT_DETECTED)
    val moduleVersion: StateFlow<String> = _moduleVersion.asStateFlow()

    private val _config = MutableStateFlow(ModuleConfig())
    val config: StateFlow<ModuleConfig> = _config.asStateFlow()

    private val _configPath = MutableStateFlow(NOT_FOUND)
    val configPath: StateFlow<String> = _configPath.asStateFlow()

    private val _saveStatus = MutableStateFlow(SaveStatus.IDLE)
    val saveStatus: StateFlow<SaveStatus> = _saveStatus.asStateFlow()

    private var refreshJob: Job? = null

    fun setRootToggle(enabled: Boolean) {
        _isRootToggleEnabled.value = enabled
        if (enabled) {
            refreshStatus()
        } else {
            refreshJob?.cancel()
            resetState()
        }
    }

    fun refreshStatus() {
        if (!_isRootToggleEnabled.value) return // Jangan cek root jika toggle mati

        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            // Semua perintah su berjalan di background thread agar UI tidak freeze/ANR
            val snapshot = withContext(Dispatchers.IO) { loadSnapshot() }
            if (!_isRootToggleEnabled.value) return@launch // toggle dimatikan saat loading

            if (!snapshot.rootGranted) {
                // Toggle nyala tapi akses root ditolak
                _isRootToggleEnabled.value = false
                resetState()
                return@launch
            }
            _isRootGranted.value = true
            _isModuleActive.value = snapshot.moduleActive
            _moduleVersion.value = snapshot.version
            _configPath.value = snapshot.configPath
            _config.value = snapshot.config
        }
    }

    fun updateConfig(newConfig: ModuleConfig) {
        if (!_isRootGranted.value) return
        viewModelScope.launch {
            val (success, path) = withContext(Dispatchers.IO) {
                val ok = ConfigManager.writeConfig(newConfig)
                ok to (ConfigManager.getActiveConfigPath() ?: NOT_FOUND)
            }
            if (success) {
                _config.value = newConfig
                _configPath.value = path
            }
            _saveStatus.value = if (success) SaveStatus.SUCCESS else SaveStatus.FAILED
        }
    }

    fun clearSaveStatus() {
        _saveStatus.value = SaveStatus.IDLE
    }

    private fun loadSnapshot(): ModuleSnapshot {
        if (!RootUtil.isRootAvailable()) return ModuleSnapshot(rootGranted = false)

        val installed = ConfigManager.isModuleInstalled()
        val path = ConfigManager.getActiveConfigPath()
        return ModuleSnapshot(
            rootGranted = true,
            moduleActive = installed,
            version = if (installed) ConfigManager.getModuleProp("version") else NOT_DETECTED,
            configPath = path ?: NOT_FOUND,
            config = if (path != null) ConfigManager.readConfig(path) else ModuleConfig()
        )
    }

    private fun resetState() {
        _isRootGranted.value = false
        _isModuleActive.value = false
        _moduleVersion.value = NOT_DETECTED
        _configPath.value = NOT_FOUND
        _config.value = ModuleConfig()
        _saveStatus.value = SaveStatus.IDLE
    }
}
