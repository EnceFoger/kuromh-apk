package com.kuromh.controller.core

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.util.zip.ZipFile

/**
 * Install / status / on-off modul Zygisk Kuro Mirror High.
 * Semua perintah root memblokir, jadi panggil dari background thread.
 */
object ModuleManager {
    const val MODULE_ID = "kuro_mirror_high"
    const val BUNDLED_ASSET = "kuro_mirror_high.zip"

    private const val WATCHDOG_ASSET = "uninstall_watchdog.sh"
    private const val MODULE_PATH = "/data/adb/modules/$MODULE_ID"
    private const val UPDATE_PATH = "/data/adb/modules_update/$MODULE_ID"
    private const val STAGED_ZIP = "/data/local/tmp/kuromh_module_install.zip"

    private const val K_BOOT_ID = "module_installed_boot_id"
    private const val K_SAVED = "module_saved_valid"
    private const val K_UNITY = "module_saved_unity"
    private const val K_DAMAGE = "module_saved_damage"
    private const val K_GOD = "module_saved_god"
    private const val K_DUMP = "module_saved_dump"

    data class Result(val ok: Boolean, val message: String)

    // ---------------------------------------------------------------- status

    fun isInstalled(): Boolean =
        RootUtil.execute("[ -f '$MODULE_PATH/module.prop' ] && echo yes") == "yes"

    /** Modul aktif (tidak di-disable). Saat OFF, package.txt diganti nama sehingga modul tidak memuat hook. */
    fun isEnabled(): Boolean = RootUtil.execute(
        "[ -f '$MODULE_PATH/module.prop' ] && [ ! -f '$MODULE_PATH/disable' ] && [ -f '$MODULE_PATH/package.txt' ] && echo yes"
    ) == "yes"

    /** True sejak modul di-install sampai perangkat reboot (Zygisk baru memuat modul saat boot). */
    fun isRebootPending(prefs: SharedPreferences): Boolean {
        if (RootUtil.execute("[ -d '$UPDATE_PATH' ] && echo yes") == "yes") return true
        val saved = prefs.getString(K_BOOT_ID, null) ?: return false
        return saved == currentBootId()
    }

    private fun currentBootId(): String =
        try { File("/proc/sys/kernel/random/boot_id").readText().trim() } catch (e: Exception) { "" }

    // --------------------------------------------------------------- install

    /** Null jika zip valid; selain itu pesan error. Hanya modul dengan id kuro_mirror_high yang diterima. */
    private fun validate(zip: File): String? = try {
        ZipFile(zip).use { z ->
            val prop = z.getEntry("module.prop")
                ?: return@use "Not a module zip: module.prop not found."
            val id = z.getInputStream(prop).bufferedReader().useLines { lines ->
                lines.map { it.trim() }
                    .firstOrNull { it.startsWith("id=") }
                    ?.removePrefix("id=")?.trim()
            }
            if (id != MODULE_ID) {
                "Wrong module (id=${id ?: "?"}). Expected $MODULE_ID."
            } else if (!hasZygiskLib(z)) {
                "Module zip has no zygisk library."
            } else {
                null
            }
        }
    } catch (e: Exception) {
        "Invalid zip file."
    }

    private fun hasZygiskLib(z: ZipFile): Boolean {
        val en = z.entries()
        while (en.hasMoreElements()) {
            val e = en.nextElement()
            if (!e.isDirectory && e.name.startsWith("zygisk/")) return true
        }
        return false
    }

    fun install(context: Context, zip: File, prefs: SharedPreferences): Result {
        validate(zip)?.let { return Result(false, it) }

        val staged = RootUtil.execute(
            "cp '${zip.absolutePath}' '$STAGED_ZIP' && chmod 644 '$STAGED_ZIP' && echo ok"
        )
        if (staged != "ok") return Result(false, "Could not stage the module file with root.")

        try {
            RootUtil.execute("rm -rf '$UPDATE_PATH'")
            val output = RootUtil.execute(installerCommand())
            var via = output.lineSequence().firstOrNull()?.trim().orEmpty()
            var ok = RootUtil.execute("[ -f '$UPDATE_PATH/module.prop' ] && echo yes") == "yes"

            if (!ok) {
                // Installer bawaan root manager tidak ada / gagal -> pasang manual ke folder modul
                ok = manualInstall(context, zip)
                via = "manual"
            }
            if (!ok) {
                return Result(false, "Module installer failed. Use \"Upload Module\" to try another file, or check your root manager.")
            }

            deployWatchdog(context)
            prefs.edit().putString(K_BOOT_ID, currentBootId()).apply()
            return Result(true, "Module installed${if (via.isNotEmpty()) " ($via)" else ""}. Reboot your device to activate it.")
        } finally {
            RootUtil.execute("rm -f '$STAGED_ZIP'")
        }
    }

    private fun installerCommand(): String =
        "if command -v ksud >/dev/null 2>&1; then echo ksud; ksud module install '$STAGED_ZIP'; " +
        "elif command -v apd >/dev/null 2>&1; then echo apd; apd module install '$STAGED_ZIP'; " +
        "elif command -v magisk >/dev/null 2>&1; then echo magisk; magisk --install-module '$STAGED_ZIP'; fi"

    /** Ekstrak zip di aplikasi (dengan perlindungan zip-slip) lalu salin ke /data/adb/modules lewat root. */
    private fun manualInstall(context: Context, zip: File): Boolean {
        return try {
            val dir = File(context.cacheDir, "module_extract")
            dir.deleteRecursively()
            dir.mkdirs()
            val base = dir.canonicalPath + File.separator
            ZipFile(zip).use { z ->
                val en = z.entries()
                while (en.hasMoreElements()) {
                    val e = en.nextElement()
                    if (e.isDirectory || e.name.startsWith("META-INF/")) continue
                    val out = File(dir, e.name)
                    if (!out.canonicalPath.startsWith(base)) continue
                    out.parentFile?.mkdirs()
                    z.getInputStream(e).use { input -> out.outputStream().use { output -> input.copyTo(output) } }
                }
            }
            val d = MODULE_PATH
            val result = RootUtil.execute(
                "rm -rf '$d' && mkdir -p '$d' && cp -r '${dir.absolutePath}/.' '$d/' && " +
                "find '$d' -type d -exec chmod 755 {} + && find '$d' -type f -exec chmod 644 {} + && " +
                "chmod 755 '$d'/zygisk/*.so && " +
                "(chown -R 0:0 '$d'; chcon -R u:object_r:system_file:s0 '$d'; true) && " +
                "[ -f '$d/module.prop' ] && echo ok"
            )
            dir.deleteRecursively()
            result == "ok"
        } catch (e: Exception) {
            false
        }
    }

    /** Pasang service.sh yang menghapus modul otomatis jika aplikasi KUROMH di-uninstall. */
    private fun deployWatchdog(context: Context) {
        try {
            val tmp = File(context.cacheDir, WATCHDOG_ASSET)
            val text = context.assets.open(WATCHDOG_ASSET).bufferedReader().use { it.readText() }
            tmp.writeText(text.replace("\r\n", "\n"))
            for (dir in listOf(UPDATE_PATH, MODULE_PATH)) {
                RootUtil.execute(
                    "if [ -d '$dir' ]; then cp '${tmp.absolutePath}' '$dir/service.sh' && " +
                    "chmod 755 '$dir/service.sh' && chown 0:0 '$dir/service.sh'; " +
                    "chcon u:object_r:system_file:s0 '$dir/service.sh'; fi; true"
                )
            }
            tmp.delete()
        } catch (e: Exception) {
            // Watchdog gagal dipasang tidak boleh menggagalkan install
        }
    }

    // ------------------------------------------------------------- on / off

    /**
     * OFF: nilai config dinetralkan (game yang sedang jalan langsung normal, config dibaca ulang tiap ~2 detik)
     * dan package.txt diganti nama sehingga modul tidak memasang hook lagi pada peluncuran game berikutnya.
     * ON: kebalikannya, nilai config sebelumnya dikembalikan.
     */
    fun setEnabled(prefs: SharedPreferences, enabled: Boolean): Boolean {
        if (!isInstalled()) return false
        if (enabled) {
            RootUtil.execute(
                "rm -f '$MODULE_PATH/disable'; " +
                "[ -f '$MODULE_PATH/package.txt.off' ] && mv '$MODULE_PATH/package.txt.off' '$MODULE_PATH/package.txt'; true"
            )
            restoreConfig(prefs)
        } else {
            neutralizeConfig(prefs)
            RootUtil.execute(
                "touch '$MODULE_PATH/disable'; " +
                "[ -f '$MODULE_PATH/package.txt' ] && mv '$MODULE_PATH/package.txt' '$MODULE_PATH/package.txt.off'; true"
            )
        }
        return isEnabled() == enabled
    }

    private fun neutralizeConfig(prefs: SharedPreferences) {
        val path = ConfigManager.getActiveConfigPath() ?: return
        val current = ConfigManager.readConfig(path)
        if (isEnabled()) {
            // Simpan nilai asli hanya saat modul masih ON, supaya OFF dua kali tidak menimpanya dengan nilai netral
            prefs.edit()
                .putBoolean(K_SAVED, true)
                .putFloat(K_UNITY, current.unitySpeed)
                .putFloat(K_DAMAGE, current.damageRate)
                .putBoolean(K_GOD, current.godMode)
                .putBoolean(K_DUMP, current.dump)
                .apply()
        }
        ConfigManager.writeConfig(
            current.copy(unitySpeed = 1.0f, damageRate = 1.0f, godMode = false, dump = false)
        )
    }

    private fun restoreConfig(prefs: SharedPreferences) {
        if (!prefs.getBoolean(K_SAVED, false)) return
        val path = ConfigManager.getActiveConfigPath() ?: return
        val current = ConfigManager.readConfig(path)
        ConfigManager.writeConfig(
            current.copy(
                unitySpeed = prefs.getFloat(K_UNITY, 1.0f),
                damageRate = prefs.getFloat(K_DAMAGE, 1.0f),
                godMode = prefs.getBoolean(K_GOD, false),
                dump = prefs.getBoolean(K_DUMP, false)
            )
        )
        prefs.edit().putBoolean(K_SAVED, false).apply()
    }
}
