package com.kuromh.controller.core

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class SaveStatus { IDLE, SUCCESS, FAILED }

enum class InstallStatus { IDLE, RUNNING, SUCCESS, FAILED }

data class InstallState(val status: InstallStatus = InstallStatus.IDLE, val message: String = "")

private data class ModuleSnapshot(
    val rootGranted: Boolean,
    val installed: Boolean = false,
    val enabled: Boolean = false,
    val rebootPending: Boolean = false,
    val version: String = MainViewModel.NOT_DETECTED,
    val configPath: String = MainViewModel.NOT_FOUND,
    val config: ModuleConfig = ModuleConfig()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val NOT_DETECTED = "Not Detected"
        const val NOT_FOUND = "Not Found"
        private const val PREFS_NAME = "kuromh_prefs"
        private const val KEY_ROOT_TOGGLE = "root_toggle_enabled"
        // Status modul terakhir yang terverifikasi, supaya saat aplikasi dibuka ulang tampilan tidak mulai dari kosong
        private const val KEY_CACHE_VALID = "cache_valid"
        private const val KEY_CACHE_INSTALLED = "cache_installed"
        private const val KEY_CACHE_ENABLED = "cache_enabled"
        private const val KEY_CACHE_PENDING = "cache_reboot_pending"
        private const val KEY_CACHE_VERSION = "cache_version"
    }

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Apakah pengguna mengizinkan aplikasi memakai Root (disimpan, tetap aktif setelah aplikasi dibuka ulang)
    private val _isRootToggleEnabled = MutableStateFlow(prefs.getBoolean(KEY_ROOT_TOGGLE, false))
    val isRootToggleEnabled: StateFlow<Boolean> = _isRootToggleEnabled.asStateFlow()

    // Dipakai hanya jika root memang sudah diizinkan sebelumnya; akan dikoreksi oleh refreshStatus()
    private val cacheValid = prefs.getBoolean(KEY_ROOT_TOGGLE, false) && prefs.getBoolean(KEY_CACHE_VALID, false)
    private val cInstalled = cacheValid && prefs.getBoolean(KEY_CACHE_INSTALLED, false)
    private val cEnabled = cInstalled && prefs.getBoolean(KEY_CACHE_ENABLED, false)
    private val cPending = cInstalled && prefs.getBoolean(KEY_CACHE_PENDING, false)

    private val _isRootGranted = MutableStateFlow(cacheValid)
    val isRootGranted: StateFlow<Boolean> = _isRootGranted.asStateFlow()

    /** Modul terpasang, sudah aktif (bukan menunggu reboot) dan tidak di-OFF. */
    private val _isModuleActive = MutableStateFlow(cInstalled && cEnabled && !cPending)
    val isModuleActive: StateFlow<Boolean> = _isModuleActive.asStateFlow()

    private val _moduleInstalled = MutableStateFlow(cInstalled)
    val moduleInstalled: StateFlow<Boolean> = _moduleInstalled.asStateFlow()

    private val _moduleEnabled = MutableStateFlow(cEnabled)
    val moduleEnabled: StateFlow<Boolean> = _moduleEnabled.asStateFlow()

    private val _moduleRebootPending = MutableStateFlow(cPending)
    val moduleRebootPending: StateFlow<Boolean> = _moduleRebootPending.asStateFlow()

    private val _moduleVersion = MutableStateFlow(
        if (cInstalled) prefs.getString(KEY_CACHE_VERSION, NOT_DETECTED) ?: NOT_DETECTED else NOT_DETECTED
    )
    val moduleVersion: StateFlow<String> = _moduleVersion.asStateFlow()

    private val _config = MutableStateFlow(ModuleConfig())
    val config: StateFlow<ModuleConfig> = _config.asStateFlow()

    private val _configPath = MutableStateFlow(NOT_FOUND)
    val configPath: StateFlow<String> = _configPath.asStateFlow()

    private val _saveStatus = MutableStateFlow(SaveStatus.IDLE)
    val saveStatus: StateFlow<SaveStatus> = _saveStatus.asStateFlow()

    private val _installState = MutableStateFlow(InstallState())
    val installState: StateFlow<InstallState> = _installState.asStateFlow()

    private val _toggleFailed = MutableStateFlow(false)
    val toggleFailed: StateFlow<Boolean> = _toggleFailed.asStateFlow()

    private val _moduleToggleBusy = MutableStateFlow(false)
    val moduleToggleBusy: StateFlow<Boolean> = _moduleToggleBusy.asStateFlow()


    private val _otherModules = MutableStateFlow<List<OtherModuleManager.Module>>(emptyList())
    val otherModules: StateFlow<List<OtherModuleManager.Module>> = _otherModules.asStateFlow()

    private val _otherModuleResult = MutableStateFlow("")
    val otherModuleResult: StateFlow<String> = _otherModuleResult.asStateFlow()

    private val _otherModuleBusy = MutableStateFlow(false)
    val otherModuleBusy: StateFlow<Boolean> = _otherModuleBusy.asStateFlow()

    private val _otherTogglePending = MutableStateFlow<Set<String>>(emptySet())
    val otherTogglePending: StateFlow<Set<String>> = _otherTogglePending.asStateFlow()
    private var otherRefreshRevision = 0


    fun refreshOtherModules() {
        val revision = ++otherRefreshRevision
        if (!_isRootGranted.value) {
            _otherModules.value = emptyList()
            return
        }
        viewModelScope.launch {
            val listed = withContext(Dispatchers.IO) { OtherModuleManager.list() }
            if (revision != otherRefreshRevision || !_isRootGranted.value) return@launch
            // Keep immediate switch feedback while an in-flight root command completes.
            val pending = _otherTogglePending.value
            val optimistic = _otherModules.value.associateBy { it.id }
            _otherModules.value = listed.map { fresh ->
                if (fresh.id in pending) fresh.copy(enabled = optimistic[fresh.id]?.enabled ?: fresh.enabled)
                else fresh
            }
        }
    }

    fun installOtherModule(uri: Uri) {
        if (!_isRootGranted.value || _otherModuleBusy.value) return
        _otherModuleBusy.value = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val app = getApplication<Application>()
                val target = File(app.cacheDir, "other_module_upload.zip")
                try {
                    val source = app.contentResolver.openInputStream(uri)
                        ?: return@withContext OtherModuleManager.Result(false, "Cannot open ZIP.")
                    source.use { input -> target.outputStream().use { input.copyTo(it) } }
                    OtherModuleManager.install(target)
                } catch (e: Exception) {
                    OtherModuleManager.Result(false, "Could not install module: ${e.message}")
                } finally {
                    target.delete()
                }
            }
            _otherModuleResult.value = result.message
            _otherModuleBusy.value = false
            refreshOtherModules()
        }
    }

    fun toggleOtherModule(id: String, enabled: Boolean) {
        if (!_isRootGranted.value || _otherModuleBusy.value || id in _otherTogglePending.value) return
        val old = _otherModules.value.firstOrNull { it.id == id } ?: return
        if (old.enabled == enabled) return

        ++otherRefreshRevision // Discard any older refresh that would reset this optimistic value.
        _otherTogglePending.value = _otherTogglePending.value + id
        _otherModules.value = _otherModules.value.map {
            if (it.id == id) it.copy(enabled = enabled) else it
        }
        _otherModuleResult.value = ""
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { OtherModuleManager.setEnabled(id, enabled) }
            if (!result.ok) {
                _otherModules.value = _otherModules.value.map {
                    if (it.id == id) it.copy(enabled = old.enabled) else it
                }
            }
            _otherModuleResult.value = if (result.ok) "Reboot to apply." else result.message
            _otherTogglePending.value = _otherTogglePending.value - id
            refreshOtherModules()
        }
    }

    fun removeOtherModule(id: String) {
        if (!_isRootGranted.value || _otherModuleBusy.value) return
        _otherModuleBusy.value = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { OtherModuleManager.uninstall(id) }
            _otherModuleResult.value = result.message
            _otherModuleBusy.value = false
            refreshOtherModules()
        }
    }


    private val _externalPanel = MutableStateFlow<ExternalModControls.Panel?>(null)
    val externalPanel: StateFlow<ExternalModControls.Panel?> = _externalPanel.asStateFlow()

    private val _externalMessage = MutableStateFlow("")
    val externalMessage: StateFlow<String> = _externalMessage.asStateFlow()

    private val _externalBusy = MutableStateFlow(false)
    val externalBusy: StateFlow<Boolean> = _externalBusy.asStateFlow()


    private val _selectedGame = MutableStateFlow(ModuleGames.Game())
    val selectedGame: StateFlow<ModuleGames.Game> = _selectedGame.asStateFlow()

    private val _gameLaunchMessage = MutableStateFlow("")
    val gameLaunchMessage: StateFlow<String> = _gameLaunchMessage.asStateFlow()

    private var currentGameModuleId: String? = null

    fun detectGame(moduleId: String) {
        currentGameModuleId = moduleId
        _selectedGame.value = ModuleGames.Game()
        _gameLaunchMessage.value = ""
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) {
                ModuleGames.detect(getApplication<Application>(), moduleId, _isRootGranted.value)
            }
            if (currentGameModuleId == moduleId) _selectedGame.value = info
        }
    }

    fun openSelectedGame() {
        val game = _selectedGame.value
        _gameLaunchMessage.value =
            if (ModuleGames.launch(getApplication(), game)) ""
            else "Could not launch the game. Verify it is installed and enabled."
    }

    private var selectedExternalModule: String? = null

    fun openExternalControls(id: String) {
        selectedExternalModule = id
        _externalPanel.value = null
        _externalMessage.value = "Loading mod controls..."
        if (!_isRootGranted.value) {
            _externalMessage.value = "Enable root first."
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { ExternalModControls.load(id) }
            if (selectedExternalModule == id) {
                _externalPanel.value = result.panel
                _externalMessage.value = result.message
            }
        }
    }

    fun closeExternalControls() {
        selectedExternalModule = null
        _externalPanel.value = null
        _externalMessage.value = ""
    }

    fun saveExternalSetting(id: String, key: String, value: String) {
        if (!_isRootGranted.value || _externalBusy.value || selectedExternalModule != id) return
        val before = _externalPanel.value?.takeIf { it.moduleId == id } ?: return
        if (before.controls.none { it.key == key }) return

        // Display the requested state immediately; a failure restores the previous state.
        _externalPanel.value = before.copy(
            controls = before.controls.map { if (it.key == key) it.copy(value = value) else it }
        )
        _externalMessage.value = ""
        _externalBusy.value = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { ExternalModControls.save(id, key, value) }
            if (selectedExternalModule == id) {
                if (result.ok) {
                    val reloaded = withContext(Dispatchers.IO) { ExternalModControls.load(id).panel }
                    if (reloaded != null) _externalPanel.value = reloaded
                } else {
                    _externalPanel.value = before
                }
                _externalMessage.value = if (result.ok) "" else result.message
            }
            _externalBusy.value = false
        }
    }

    private var refreshJob: Job? = null

    init {
        // Jika toggle tersimpan aktif, langsung cek root & modul saat aplikasi dibuka
        refreshStatus()
    }

    fun setRootToggle(enabled: Boolean) {
        _isRootToggleEnabled.value = enabled
        prefs.edit().putBoolean(KEY_ROOT_TOGGLE, enabled).apply()
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
                prefs.edit().putBoolean(KEY_ROOT_TOGGLE, false).apply()
                resetState()
                return@launch
            }
            prefs.edit()
                .putBoolean(KEY_CACHE_VALID, true)
                .putBoolean(KEY_CACHE_INSTALLED, snapshot.installed)
                .putBoolean(KEY_CACHE_ENABLED, snapshot.enabled)
                .putBoolean(KEY_CACHE_PENDING, snapshot.rebootPending)
                .putString(KEY_CACHE_VERSION, snapshot.version)
                .apply()
            _isRootGranted.value = true
            refreshOtherModules()
            _moduleInstalled.value = snapshot.installed
            _moduleEnabled.value = snapshot.enabled
            _moduleRebootPending.value = snapshot.rebootPending
            _isModuleActive.value = snapshot.installed && snapshot.enabled && !snapshot.rebootPending
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

    // ---------------------------------------------------------------- modul

    /**
     * Pasang modul: coba unduh dari Supabase (hash diverifikasi), kalau gagal
     * (offline / file belum ada / hash tidak cocok) pakai zip bawaan APK (assets/kuro_mirror_high.zip).
     */
    fun installBundledModule() {
        runInstall { app ->
            val out = File(app.cacheDir, "module_install.zip")
            if (RemoteModule.download(out)) return@runInstall out
            app.assets.open(ModuleManager.BUNDLED_ASSET).use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            out
        }
    }

    /** Pasang modul dari file zip pilihan pengguna ("Upload Module"). */
    fun installFromUri(uri: Uri) {
        runInstall { app ->
            val input = app.contentResolver.openInputStream(uri) ?: return@runInstall null
            val out = File(app.cacheDir, "module_install.zip")
            input.use { src -> out.outputStream().use { dst -> src.copyTo(dst) } }
            out
        }
    }

    private fun runInstall(prepare: (Application) -> File?) {
        if (_installState.value.status == InstallStatus.RUNNING) return
        if (!_isRootGranted.value) {
            _installState.value = InstallState(InstallStatus.FAILED, "Enable Root System in Home first.")
            return
        }
        _installState.value = InstallState(InstallStatus.RUNNING, "Installing module...")
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val app = getApplication<Application>()
                val zip = try { prepare(app) } catch (e: Exception) { null }
                if (zip == null) {
                    ModuleManager.Result(false, "Could not read the module file.")
                } else {
                    try {
                        ModuleManager.install(app, zip, prefs)
                    } finally {
                        zip.delete()
                    }
                }
            }
            _installState.value = InstallState(
                if (result.ok) InstallStatus.SUCCESS else InstallStatus.FAILED,
                result.message
            )
            refreshStatus()
        }
    }

    fun clearInstallState() {
        if (_installState.value.status != InstallStatus.RUNNING) _installState.value = InstallState()
    }

    /** Toggle ON/OFF modul. OFF = modul langsung berhenti bekerja (tanpa reboot). */
    fun setModuleEnabled(enabled: Boolean) {
        if (!_isRootGranted.value || !_moduleInstalled.value || _moduleToggleBusy.value) return
        if (_moduleEnabled.value == enabled) return
        val previous = _moduleEnabled.value
        refreshJob?.cancel() // Prevent a previous root query from overriding the new switch state.
        _moduleToggleBusy.value = true
        _toggleFailed.value = false
        _moduleEnabled.value = enabled
        _isModuleActive.value = enabled && !_moduleRebootPending.value
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { ModuleManager.setEnabled(prefs, enabled) }
            if (!ok) {
                _moduleEnabled.value = previous
                _isModuleActive.value = previous && !_moduleRebootPending.value
                _toggleFailed.value = true
            }
            _moduleToggleBusy.value = false
            refreshStatus()
        }
    }

    private fun loadSnapshot(): ModuleSnapshot {
        if (!RootUtil.isRootAvailable()) return ModuleSnapshot(rootGranted = false)

        val installed = ModuleManager.isInstalled()
        val path = ConfigManager.getActiveConfigPath()
        return ModuleSnapshot(
            rootGranted = true,
            installed = installed,
            enabled = installed && ModuleManager.isEnabled(),
            rebootPending = installed && ModuleManager.isRebootPending(prefs),
            version = if (installed) ConfigManager.getModuleProp("version") else NOT_DETECTED,
            configPath = path ?: NOT_FOUND,
            config = if (path != null) ConfigManager.readConfig(path) else ModuleConfig()
        )
    }

    private fun resetState() {
        prefs.edit().putBoolean(KEY_CACHE_VALID, false).apply()
        _isRootGranted.value = false
        _otherModules.value = emptyList()
        _otherTogglePending.value = emptySet()
        _moduleToggleBusy.value = false
        _isModuleActive.value = false
        _moduleInstalled.value = false
        _moduleEnabled.value = false
        _moduleRebootPending.value = false
        _moduleVersion.value = NOT_DETECTED
        _configPath.value = NOT_FOUND
        _config.value = ModuleConfig()
        _saveStatus.value = SaveStatus.IDLE
        _toggleFailed.value = false
    }
}
