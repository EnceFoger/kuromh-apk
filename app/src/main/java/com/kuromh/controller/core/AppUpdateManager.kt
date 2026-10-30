package com.kuromh.controller.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Public GitHub Releases only. Never installs arbitrary URLs or silent packages. */
object AppUpdateManager {
    private const val OWNER_REPO = "EnceFoger/kuromh-apk"
    private const val RELEASE_API = "https://api.github.com/repos/$OWNER_REPO/releases/latest"
    private const val MAX_APK_SIZE = 100L * 1024 * 1024
    private val versionPattern = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:\.(\d+))?(?:$|[-+])""")

    data class Release(
        val version: String,
        val notes: String,
        val downloadUrl: String,
        val expectedSize: Long,
        val sha256: String?
    )

    private fun versionParts(text: String): List<Int>? {
        val m = versionPattern.find(text) ?: return null
        return (1..4).map { n -> m.groupValues[n].toIntOrNull() ?: 0 }
    }

    private fun newer(candidate: String, installed: String): Boolean {
        val a = versionParts(candidate) ?: return false
        val b = versionParts(installed) ?: return false
        for (i in a.indices) {
            if (a[i] != b[i]) return a[i] > b[i]
        }
        return false
    }

    private fun installedVersion(context: Context): String {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return info.versionName ?: "0.0.0"
    }

    /** Null means the user already has the newest published full release. */
    fun findUpdate(context: Context): Release? {
        val conn = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "KUROMH-Android-Updater")
        }
        val body = try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK)
                throw IOException("GitHub Releases error: HTTP " + conn.responseCode)
            conn.inputStream.bufferedReader().use { it.readText().take(250000) }
        } finally {
            conn.disconnect()
        }
        val json = JSONObject(body)
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) return null
        val releaseVersion = json.optString("tag_name").let { versionPattern.find(it)?.value }
            ?.removePrefix("v")?.substringBefore('-')?.substringBefore('+')
            ?: throw IOException("Latest GitHub Release has no valid version.")
        if (!newer(releaseVersion, installedVersion(context))) return null

        val assetName = "KUROMH-v" + releaseVersion + "-Release.apk"
        val assets = json.optJSONArray("assets") ?: throw IOException("Release APK is missing.")
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (asset.optString("name") != assetName) continue
            val url = asset.optString("browser_download_url")
            if (!url.startsWith("https://github.com/$OWNER_REPO/releases/download/"))
                throw IOException("Untrusted update URL.")
            val size = asset.optLong("size")
            if (size <= 0 || size > MAX_APK_SIZE) throw IOException("Unexpected update APK size.")
            val digest = asset.optString("digest")
            val checksum = if (digest.startsWith("sha256:")) digest.removePrefix("sha256:") else null
            val notes = json.optString("body").take(600)
            return Release(releaseVersion, notes, url, size, checksum)
        }
        throw IOException("Release " + releaseVersion + " has no matching signed APK.")
    }

    private fun openApprovedDownload(address: String): HttpURLConnection {
        var target = URL(address)
        repeat(8) {
            val host = target.host.lowercase()
            if (target.protocol != "https" ||
                (host != "github.com" && !host.endsWith(".githubusercontent.com"))
            ) throw IOException("Update download redirected to an untrusted host.")
            val conn = (target.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("User-Agent", "KUROMH-Android-Updater")
            }
            val status = conn.responseCode
            if (status in 300..399) {
                val location = conn.getHeaderField("Location")
                    ?: throw IOException("Missing download redirect.")
                target = URL(target, location)
                conn.disconnect()
            } else if (status == HttpURLConnection.HTTP_OK) {
                return conn
            } else {
                conn.disconnect()
                throw IOException("APK download failed: HTTP $status")
            }
        }
        throw IOException("Too many download redirects.")
    }

    /** Download to private cache, verify official SHA-256 (when provided) and APK identity. */
    fun download(context: Context, release: Release, onProgress: (Int) -> Unit): File {
        val folder = File(context.cacheDir, "updates").apply { mkdirs() }
        val temp = File(folder, "kuromh-staging.apk")
        val ready = File(folder, "kuromh-update.apk")
        temp.delete()
        val digest = MessageDigest.getInstance("SHA-256")
        val conn = openApprovedDownload(release.downloadUrl)
        try {
            var bytesReadTotal = 0L
            conn.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(16384)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        bytesReadTotal += read
                        if (bytesReadTotal > MAX_APK_SIZE) throw IOException("APK is too large.")
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        onProgress(((bytesReadTotal * 100) / release.expectedSize)
                            .toInt().coerceIn(0, 100))
                    }
                }
            }
            if (bytesReadTotal != release.expectedSize)
                throw IOException("Download is incomplete.")
            val actualSha = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            if (release.sha256 != null &&
                !actualSha.equals(release.sha256, ignoreCase = true)
            ) throw IOException("APK SHA-256 does not match GitHub Release.")

            @Suppress("DEPRECATION")
            val archive = context.packageManager.getPackageArchiveInfo(temp.absolutePath, 0)
                ?: throw IOException("Downloaded file is not a valid APK.")
            if (archive.packageName != context.packageName)
                throw IOException("Update package name does not match KUROMH.")
            @Suppress("DEPRECATION")
            val current = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            val archiveCode = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode
                else archive.versionCode.toLong()
            @Suppress("DEPRECATION")
            val currentCode = if (Build.VERSION.SDK_INT >= 28) current.longVersionCode
                else current.versionCode.toLong()
            if (archiveCode <= currentCode)
                throw IOException("Update versionCode must be higher than installed APK.")

            if (ready.exists()) ready.delete()
            if (!temp.renameTo(ready)) throw IOException("Failed to prepare downloaded APK.")
            return ready
        } finally {
            conn.disconnect()
            temp.delete()
        }
    }

    /**
     * Android's installer must confirm the update; no root/silent install.
     * If unknown-app installs are blocked, take the user to the relevant setting.
     */
    fun openInstaller(context: Context, apk: File): String? {
        if (!apk.isFile) return "APK not found. Please download it again."
        return try {
            if (!context.packageManager.canRequestPackageInstalls()) {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:" + context.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
                "Allow KUROMH to install updates, then return and tap Install."
            } else {
                val uri = FileProvider.getUriForFile(
                    context, context.packageName + ".updates", apk
                )
                context.startActivity(
                    Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                        data = uri
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
                null
            }
        } catch (error: Exception) {
            "Could not open Android installer: " + (error.message ?: "unknown error")
        }
    }
}
