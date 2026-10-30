package com.kuromh.controller.core

import java.util.Locale

/**
 * Menghubungkan app dengan file kuro_mirror_high.cfg yang dibaca modul Zygisk
 * dari dalam proses game (bukan dari folder modul di /data/adb/modules).
 */
object ConfigManager {
    private const val MODULE_DIR = "/data/adb/modules/kuro_mirror_high"
    private const val CONFIG_NAME = "kuro_mirror_high.cfg"
    private const val DEFAULT_PACKAGE = "com.bandainamcoent.bleach_mh_ww"

    /** Package game target, dibaca dari package.txt milik modul (sama seperti yang dipakai modul). */
    private fun gamePackage(): String {
        val pkg = RootUtil.execute("grep -v '^#' '$MODULE_DIR/package.txt' | head -n 1").trim()
        return pkg.ifEmpty { DEFAULT_PACKAGE }
    }

    /** Semua lokasi config milik modul yang file-nya sudah ada (dibuat modul saat game dijalankan). */
    fun getConfigPaths(): List<String> {
        val pkg = gamePackage()
        val candidates = listOf(
            "/storage/emulated/0/Android/data/$pkg/files/$CONFIG_NAME",
            "/data/media/0/Android/data/$pkg/files/$CONFIG_NAME",
            "/data/data/$pkg/files/$CONFIG_NAME"
        )
        val list = candidates.joinToString(" ") { "'$it'" }
        return RootUtil.execute("for p in $list; do if [ -f \"\$p\" ]; then echo \"\$p\"; fi; done")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /** Path config pertama yang ada, atau null jika modul belum membuat config. */
    fun getActiveConfigPath(): String? = getConfigPaths().firstOrNull()

    fun isModuleInstalled(): Boolean =
        RootUtil.execute("[ -d '$MODULE_DIR' ] && echo yes") == "yes"

    fun getModuleProp(key: String): String {
        val output = RootUtil.execute(
            "grep '^$key=' '$MODULE_DIR/module.prop' | head -n 1 | cut -d= -f2-"
        )
        return output.ifEmpty { "Unknown" }
    }

    fun readConfig(path: String): ModuleConfig {
        val values = parse(RootUtil.execute("cat '$path'"))
        val d = ModuleConfig()
        return ModuleConfig(
            unitySpeed = values["unity_speed"].toFloatLenient() ?: d.unitySpeed,
            damageRate = values["damage_rate"].toFloatLenient() ?: d.damageRate,
            godMode = values["god_mode"]?.toIntOrNull()?.let { it != 0 } ?: d.godMode,
            dump = values["dump"]?.toIntOrNull()?.let { it != 0 } ?: d.dump,
            maxDamageRate = values["max_damage_rate"].toFloatLenient() ?: d.maxDamageRate,
            maxUnitySpeed = values["max_unity_speed"].toFloatLenient() ?: d.maxUnitySpeed
        )
    }

    /**
     * Update file config di semua lokasi yang ada. File diedit di tempat (baris lain seperti
     * "stage=" dan komentar dipertahankan, pemilik file tidak berubah) lalu diverifikasi.
     * Return false jika config belum ada atau ada yang gagal ditulis.
     */
    fun writeConfig(config: ModuleConfig): Boolean {
        val paths = getConfigPaths()
        if (paths.isEmpty()) return false

        return paths.all { path ->
            val original = RootUtil.execute("cat '$path'")
            val merged = merge(original, config)
            RootUtil.execute("cat > '$path' <<'KUROMH_EOF'\n$merged\nKUROMH_EOF")
            RootUtil.execute("cat '$path'").trim() == merged.trim()
        }
    }

    // Format modul: "key=value   # komentar". Komentar & baris kosong diabaikan.
    private fun parse(text: String): Map<String, String> =
        text.lineSequence()
            .map { it.substringBefore('#') }
            .mapNotNull { body ->
                val idx = body.indexOf('=')
                if (idx <= 0) null else body.substring(0, idx).trim() to body.substring(idx + 1).trim()
            }
            .toMap()

    private fun merge(original: String, config: ModuleConfig): String {
        // Locale.US wajib: di locale Indonesia "%.1f" menghasilkan "1,0" dan merusak file config.
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
            val newValue = managed[key] ?: return@map line
            written += key
            val comment = if (line.contains('#')) "   #" + line.substringAfter('#') else ""
            "$key=$newValue$comment"
        }.toMutableList()

        managed.forEach { (key, value) -> if (key !in written) lines.add("$key=$value") }
        return lines.joinToString("\n").trimEnd() + "\n"
    }

    private fun String?.toFloatLenient(): Float? = this?.replace(',', '.')?.toFloatOrNull()
}
