package com.kuromh.controller.core

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel : ViewModel() {
    // State untuk menyimpan apakah pengguna mengizinkan aplikasi menggunakan Root
    private val _isRootToggleEnabled = MutableStateFlow(false)
    val isRootToggleEnabled: StateFlow<Boolean> = _isRootToggleEnabled.asStateFlow()

    private val _isRootGranted = MutableStateFlow(false)
    val isRootGranted: StateFlow<Boolean> = _isRootGranted.asStateFlow()

    private val _isModuleActive = MutableStateFlow(false)
    val isModuleActive: StateFlow<Boolean> = _isModuleActive.asStateFlow()

    private val _moduleVersion = MutableStateFlow("Not Detected")
    val moduleVersion: StateFlow<String> = _moduleVersion.asStateFlow()

    private val _config = MutableStateFlow(ModuleConfig())
    val config: StateFlow<ModuleConfig> = _config.asStateFlow()
    
    private val _configPath = MutableStateFlow("Not Found")
    val configPath: StateFlow<String> = _configPath.asStateFlow()

    fun setRootToggle(enabled: Boolean) {
        _isRootToggleEnabled.value = enabled
        if (enabled) {
            refreshStatus()
        } else {
            // Reset semua status jika root toggle dimatikan
            _isRootGranted.value = false
            _isModuleActive.value = false
            _moduleVersion.value = "Not Detected"
            _configPath.value = "Not Found"
            _config.value = ModuleConfig()
        }
    }

    fun refreshStatus() {
        if (!_isRootToggleEnabled.value) return // Jangan cek root jika toggle mati

        val root = RootUtil.isRootAvailable()
        _isRootGranted.value = root

        if (root) {
            _isModuleActive.value = ConfigManager.isModuleInstalled()
            if (_isModuleActive.value) {
                _moduleVersion.value = ConfigManager.getModuleProp("version")
            } else {
                _moduleVersion.value = "Not Detected"
            }
            
            _configPath.value = ConfigManager.getActiveConfigPath() ?: "Not Found"
            _config.value = ConfigManager.readConfig()
        } else {
            // Jika toggle nyala tapi akses root ditolak Magisk
            _isRootToggleEnabled.value = false 
        }
    }

    fun updateConfig(newConfig: ModuleConfig) {
        if (_isRootGranted.value) {
            val success = ConfigManager.writeConfig(newConfig)
            if (success) {
                _config.value = newConfig
            }
        }
    }
}
