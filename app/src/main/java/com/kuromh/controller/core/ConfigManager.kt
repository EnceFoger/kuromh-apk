package com.kuromh.controller.core

import java.util.Locale

/**
 * Connect KUROMH with the per-game kuro_mirror_high.cfg file.
 * The Zygisk module runs in the game process and may create the file only after launch.
 * Every operation here runs from Dispatchers.IO with root permission.
 */
object ConfigManager {
    private const val MODULE_DIR = "/data/adb/modules/kuro_mirror_high"
    private const val CONFIG_NAME = "kuro_mirror_high.cfg"
    private const val DEFAULT_PACKAGE = "com.bandainamcoent.bleach_mh_ww"
    private val packagePattern = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

    data class WriteResult(val ok: Boolean, val message: String, val path: String? = null)

    private fun shellQuote(text: String) = "'" + text.replace("'", "'\\''") + "'"

    /** Get the game's exact package name from the installed module, not from APK UI state. */
    private fun gamePackage(): String {
        val output = RootUtil.execute(
            "for f in '$MODULE_DIR/package.txt' '$MODULE_DIR/package.txt.off'; do " +
                "if [ -f \"\$f\" ]; then grep -v '^#' \"\$f\" | head -n 1; break; fi; done"
        )
        val candidate = output.lineSequence().firstOrNull()?.trim().orEmpty()
        return candidate.takeIf { packagePattern.matches(it) } ?: DEFAULT_PACKAGE
    }

    private fun candidatePaths(): List<String> {
        val pkg = gamePackage()
        return listOf(
            "/data/media/0/Android/data/$pkg/files/$CONFIG_NAME",
            "/storage/emulated/0/Android/data/$pkg/files/$CONFIG_NAME",
            "/data/user/0/$pkg/files/$CONFIG_NAME",
            "/data/data/$pkg/files/$CONFIG_NAME"
        )
    }

    /** Read only files that exist. Some Android versions expose external storage via multiple aliases. */
    fun getConfigPaths(): List<String> {
        val candidates = candidatePaths()
        val args = candidates.joinToString(" ") { shellQuote(it) }
        return RootUtil.execute(
            "for p in $args; do if [ -f \"\$p\" ] && [ ! -L \"\$p\" ]; then printf '%s\\n' \"\$p\"; fi; done"
        ).lineSequence().map { it.trim() }.filter { it in candidates }.distinct().toList()
    }

    fun getActiveConfigPath(): String? = getConfigPaths().firstOrNull()

    fun isModuleInstalled(): Boolean =
        RootUtil.execute("[ -f '$MODULE_DIR/module.prop' ] && echo yes") == "yes"

    fun getModuleProp(key: String): String {
        if (!Regex("[A-Za-z0-9_]+").matches(key)) return "Unknown"
        val output = RootUtil.execute(
            "grep '^$key=' '$MODULE_DIR/module.prop' | head -n 1 | cut -d= -f2-"
        )
        return output.ifEmpty { "Unknown" }
    }

    fun readConfig(path: String): ModuleConfig {
        if (path !in candidatePaths()) return ModuleConfig()
        val values = parse(RootUtil.execute("cat " + shellQuote(path)))
        val defaults = ModuleConfig()
        return ModuleConfig(
            unitySpeed = values["unity_speed"].toFloatLenient() ?: defaults.unitySpeed,
            damageRate = values["damage_rate"].toFloatLenient() ?: defaults.damageRate,
            godMode = values["god_mode"]?.toIntOrNull()?.let { it != 0 } ?: defaults.godMode,
            dump = values["dump"]?.toIntOrNull()?.let { it != 0 } ?: defaults.dump,
            maxDamageRate = values["max_damage_rate"].toFloatLenient() ?: defaults.maxDamageRate,
            maxUnitySpeed = values["max_unity_speed"].toFloatLenient() ?: defaults.maxUnitySpeed
        )
    }

    /** Compatibility wrapper for callers such as the module ON/OFF recovery logic. */
    fun writeConfig(config: ModuleConfig): Boolean = writeConfigDetailed(config).ok

    /**
     * Write and verify config. Do not fail the entire save merely because one alias of
     * an otherwise writable config file (e.g. /storage vs /data/media) is inaccessible.
     * If there is no file yet, prepare one only inside a game files directory that
     * ALREADY exists. We never create or overwrite a game's app data directories.
     */
    fun writeConfigDetailed(config: ModuleConfig): WriteResult {
        if (!isModuleInstalled()) return WriteResult(false, "Kuro Mirror High belum terpasang.")
        val paths = getConfigPaths()
        if (paths.isEmpty()) return createConfigInExistingGameDirectory(config)

        var successPath: String? = null
        var attempted = 0
        for (path in paths) {
            val original = RootUtil.execute("cat " + shellQuote(path))
            val merged = merge(original, config)
            attempted++
            val command = "if [ -f " + shellQuote(path) + " ] && [ ! -L " +
                shellQuote(path) + " ]; then printf '%s' " + shellQuote(merged) +
                " > " + shellQuote(path) + " && echo KUROMH_WRITE_OK; fi"
            val output = RootUtil.execute(command)
            val successful = output.lineSequence().any { it.trim() == "KUROMH_WRITE_OK" } &&
                RootUtil.execute("cat " + shellQuote(path)).trim() == merged.trim()
            if (successful && successPath == null) successPath = path
        }
        return if (successPath != null) {
            WriteResult(true, "Config tersimpan.", successPath)
        } else {
            WriteResult(
                false,
                "File config ditemukan ($attempted lokasi), tetapi tidak dapat ditulis. " +
                    "Periksa akses root dan izin folder game."
            )
        }
    }

    /** Create a safe initial config when the game already has its files directory. */
    private fun createConfigInExistingGameDirectory(config: ModuleConfig): WriteResult {
        for (path in candidatePaths()) {
            val directory = path.substringBeforeLast('/')
            val ready = RootUtil.execute(
                "if [ -d " + shellQuote(directory) + " ] && [ ! -L " +
                    shellQuote(directory) + " ]; then echo KUROMH_DIR_OK; fi"
            ).contains("KUROMH_DIR_OK")
            if (!ready) continue

            val data = merge("", config)
            // Never overwrite an existing file: the module may be writing its own defaults.
            val qPath = shellQuote(path)
            val qDir = shellQuote(directory)
            val out = RootUtil.execute(
                "if [ -d $qDir ] && [ ! -L $qPath ] && [ ! -e $qPath ]; then " +
                    "umask 077; printf '%s' " + shellQuote(data) + " > $qPath || exit 1; " +
                    "owner=\$(stat -c '%u:%g' $qDir 2>/dev/null); " +
                    "if [ -n \"\$owner\" ]; then chown \"\$owner\" $qPath >/dev/null 2>&1 || true; fi; " +
                    "chmod 660 $qPath >/dev/null 2>&1 || true; " +
                    "echo KUROMH_CREATED; fi"
            )
            val confirmed = out.contains("KUROMH_CREATED") &&
                RootUtil.execute("cat $qPath").trim() == data.trim()
            if (confirmed) {
                return WriteResult(
                    true,
                    "Config dibuat. Buka ulang game bila fitur belum merespons.",
                    path
                )
            }
            // An inaccessible directory should not block all other known game file locations.
        }
        return WriteResult(
            false,
            "Config belum tersedia dan folder data game tidak dapat ditulis. " +
                "Buka game target sampai masuk menu, pastikan module aktif lalu reboot jika baru diinstal."
        )
    }

    private fun parse(text: String): Map<String, String> =
        text.lineSequence()
            .map { it.substringBefore('#') }
            .mapNotNull { body ->
                val idx = body.indexOf('=')
                if (idx <= 0) null else body.substring(0, idx).trim() to body.substring(idx + 1).trim()
            }
            .toMap()

    /** Replace just known KUROMH keys without deleting unfamiliar module settings. */
    private fun merge(original: String, config: ModuleConfig): String {
        fun f(v: Float) = String.format(Locale.US, "%.1f", v)
        val managed = linkedMapOf(
            "unity_speed" to f(config.unitySpeed),
            "damage_rate" to f(config.damageRate),
            "god_mode" to (if (config.godMode) "1" else "0"),
            "dump" to (if (config.dump) "1" else "0"),
            "max_damage_rate" to f(config.maxDamageRate),
            "max_unity_speed" to f(config.maxUnitySpeed)
        )
        val written = mutableSetOf<String>()
        val lines = original.lines().map { line ->
            if (line.trimStart().startsWith("#")) return@map line
            val body = line.substringBefore('#')
            val idx = body.indexOf('=')
            if (idx <= 0) return@map line
            val key = body.substring(0, idx).trim()
            val value = managed[key] ?: return@map line
            written += key
            val comment = if (line.contains('#')) "   #" + line.substringAfter('#') else ""
            "$key=$value$comment"
        }.toMutableList()
        managed.forEach { (key, value) -> if (key !in written) lines.add("$key=$value") }
        return lines.joinToString("\n").trimEnd() + "\n"
    }

    private fun String?.toFloatLenient(): Float? = this?.replace(',', '.')?.toFloatOrNull()
}
