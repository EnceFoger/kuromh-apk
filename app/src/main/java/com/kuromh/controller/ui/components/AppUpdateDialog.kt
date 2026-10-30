package com.kuromh.controller.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kuromh.controller.core.AppUpdateViewModel

/** User-confirmed in-app updater; Android always owns the actual install step. */
@Composable
fun AppUpdateDialog(updates: AppUpdateViewModel) {
    val state by updates.state.collectAsState()
    val release = state.release ?: return
    if (!state.dialogVisible) return
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { updates.dismissDialog() },
        title = { Text("KUROMH Update Available") },
        text = {
            Column {
                Text("New version: v" + release.version)
                if (release.notes.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text("What's New")
                    Spacer(Modifier.height(4.dp))
                    Text(release.notes.take(350))
                }
                if (state.downloading) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = state.progress / 100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Downloading " + state.progress + "%")
                }
                if (state.readyApkPath != null) {
                    Spacer(Modifier.height(8.dp))
                    Text("Download complete. Android will confirm installation.")
                }
                if (state.message.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(state.message)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !state.downloading,
                onClick = {
                    if (state.readyApkPath != null) updates.installDownloaded(context)
                    else updates.downloadUpdate()
                }
            ) {
                Text(if (state.readyApkPath != null) "Install Update" else if (state.downloading)
                    "Downloading..." else "Update Now")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !state.downloading,
                onClick = { updates.dismissDialog() }
            ) { Text("Later") }
        }
    )
}
