package com.kuromh.controller.core

import java.util.Locale

object ConfigManager {
    private const val MODULE_DIR = "/data/adb/modules/kuro_mirror_high"
    private const val CONFIG_NAME = "kuro_mirror_high.cfg"
    private const val FALLBACK_PATH = "/data/local/tmp/$CONFIG_NAME"

    // Lokasi file konfigurasi yang mungkin, urut prioritas
    private val possiblePaths = listOf(
        "$MODULE_DIR/$CONFIG_NAME",
        FALLBACK_PATH,
        "/storage/emulated/0/Android/data/com.bandainamcoent.bleach_mh_ww/files/$CONFIG_NAME"
    )

    /** Path config pertama yang ada, atau null. Satu kali panggilan su. */
    fun getActiveConfigPath(): String? {
        val list = possiblePaths.joinToString(" ") { "'$it'" }
        val result = RootUtil.execute(
            "for p in $list; do if [ -f \"\$p\" ]; then echo \"\$p\"; break; fi; done"
        )
        return result.ifEmpty { null }
    }

    fun isModuleInstalled(): Boolean =
        RootUtil.execute("[ -d '$MODULE_DIR' ] && echo yes") == "yes"

    fun getModuleProp(key: String): String {
        val output = RootUtil.execute(
            "grep '^$key=' '$MODULE_DIR/module.prop' | head -n 1 | cut -d= -f2-"
        )
        return output.ifEmpty { "Unknown" }
    }

    fun readConfig(path: String): ModuleConfig {
        val values = RootUtil.execute("cat '$path'")
            .lineSequence()
            .mapNotNull { line ->
                val idx = line.indexOf('=')
                if (idx <= 0) null else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
            }
            .toMap()

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

    /** Tulis config lalu verifikasi isi file. Return true hanya jika isinya benar-benar tertulis. */
    fun writeConfig(config: ModuleConfig): Boolean {
        val path = getActiveConfigPath() ?: defaultWritePath()
        val content = buildContent(config)

        RootUtil.execute(
            "cat > '$path' <<'KUROMH_EOF'\n$content\nKUROMH_EOF\nchmod 644 '$path'"
        )
        return RootUtil.execute("cat '$path'").trim() == content
    }

    private fun defaultWritePath(): String =
        if (isModuleInstalled()) "$MODULE_DIR/$CONFIG_NAME" else FALLBACK_PATH

    // Locale.US wajib: di locale Indonesia "%.1f" menghasilkan "1,0" dan merusak file config.
    private fun buildContent(config: ModuleConfig): String {
        fun f(v: Float) = String.format(Locale.US, "%.1f", v)
        return listOf(
            "unity_speed=${f(config.unitySpeed)}",
            "damage_rate=${f(config.damageRate)}",
            "god_mode=${if (config.godMode) 1 else 0}",
            "dump=${if (config.dump) 1 else 0}",
            "max_damage_rate=${f(config.maxDamageRate)}",
            "max_unity_speed=${f(config.maxUnitySpeed)}"
        ).joinToString("\n")
    }

    private fun String?.toFloatLenient(): Float? = this?.replace(',', '.')?.toFloatOrNull()
}
