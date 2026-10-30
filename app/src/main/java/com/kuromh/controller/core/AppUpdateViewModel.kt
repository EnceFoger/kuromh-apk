package com.kuromh.controller.core

import android.app.Application
import android.content.Context
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

data class AppUpdateState(
    val checking: Boolean = false,
    val channel: AppUpdateManager.Channel = AppUpdateManager.Channel.STABLE,
    val release: AppUpdateManager.Release? = null,
    val dialogVisible: Boolean = false,
    val downloading: Boolean = false,
    val progress: Int = 0,
    val readyApkPath: String? = null,
    val message: String = "",
    val manualStatus: String = ""
)

/** Update polling runs only while the app is used, never a background service. */
class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("kuromh_updates", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        // Default new installs to Beta while KUROMH is distributed as a pre-release.
        // Respect a user's explicitly saved Stable preference.
        AppUpdateState(channel = if (prefs.getString(CHANNEL_PREF, "beta") == "stable")
            AppUpdateManager.Channel.STABLE else AppUpdateManager.Channel.BETA)
    )
    private var checkJob: Job? = null
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    companion object {
        private const val LAST_CHECK = "last_checked_ms"
        private const val CHANNEL_PREF = "update_channel"
        private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }

    fun checkIfDue() {
        val last = prefs.getLong(LAST_CHECK, 0L)
        val now = System.currentTimeMillis()
        if (last > 0 && now >= last && now - last < CHECK_INTERVAL_MS) return
        check(manual = false)
    }

    fun checkNow() = check(manual = true)

    fun setChannel(channel: AppUpdateManager.Channel) {
        if (_state.value.channel == channel || _state.value.downloading) return
        prefs.edit()
            .putString(CHANNEL_PREF, if (channel == AppUpdateManager.Channel.BETA) "beta" else "stable")
            .remove(LAST_CHECK)
            .apply()
        checkJob?.cancel()
        _state.value = _state.value.copy(
            channel = channel,
            checking = false,
            release = null,
            dialogVisible = false,
            readyApkPath = null,
            progress = 0,
            message = "",
            manualStatus = ""
        )
        // Switching channel should recheck immediately, not wait another 24 hours.
        check(manual = true)
    }

    private fun check(manual: Boolean) {
        if (_state.value.checking || _state.value.downloading) return
        _state.value = _state.value.copy(
            checking = true, manualStatus = if (manual) "Checking updates..." else "",
            message = ""
        )
        val selectedChannel = _state.value.channel
        checkJob = viewModelScope.launch {
            try {
                val available = withContext(Dispatchers.IO) {
                    AppUpdateManager.findUpdate(getApplication<Application>(), selectedChannel)
                }
                // A stale result from the previous channel must never reopen its dialog.
                if (_state.value.channel != selectedChannel) return@launch
                prefs.edit().putLong(LAST_CHECK, System.currentTimeMillis()).apply()
                val current = _state.value
                _state.value = current.copy(
                    checking = false,
                    release = available,
                    dialogVisible = available != null,
                    progress = 0,
                    readyApkPath = null,
                    manualStatus = if (manual && available == null) "KUROMH is up to date." else ""
                )
            } catch (e: Exception) {
                if (_state.value.channel != selectedChannel) return@launch
                _state.value = _state.value.copy(
                    checking = false,
                    manualStatus = if (manual) "Update check failed: " +
                        (e.message ?: "network error") else ""
                )
            }
        }
    }

    fun dismissDialog() {
        if (_state.value.downloading) return
        _state.value = _state.value.copy(dialogVisible = false)
    }

    fun downloadUpdate() {
        val release = _state.value.release ?: return
        if (_state.value.downloading) return
        _state.value = _state.value.copy(
            downloading = true, progress = 0, message = "", readyApkPath = null
        )
        viewModelScope.launch {
            try {
                val downloaded = withContext(Dispatchers.IO) {
                    AppUpdateManager.download(getApplication<Application>(), release) { progress ->
                        _state.value = _state.value.copy(progress = progress)
                    }
                }
                _state.value = _state.value.copy(
                    downloading = false, progress = 100,
                    readyApkPath = downloaded.absolutePath,
                    message = ""
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    downloading = false,
                    message = e.message ?: "Download failed."
                )
            }
        }
    }

    fun installDownloaded(context: Context) {
        val file = _state.value.readyApkPath?.let(::File) ?: return
        val result = AppUpdateManager.openInstaller(context, file)
        _state.value = _state.value.copy(message = result ?: "")
    }
}
