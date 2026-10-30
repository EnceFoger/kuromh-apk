package com.kuromh.controller.core

data class ModuleConfig(
    var unitySpeed: Float = 1.0f,
    var damageRate: Float = 1.0f,
    var godMode: Int = 0,
    var dump: Int = 0,
    var maxDamageRate: Float = 10.0f,
    var maxUnitySpeed: Float = 5.0f
)

object ConfigManager {
    private const val MODULE_DIR = "/data/adb/modules/kuro_mirror_high"
    
    // Memeriksa berbagai kemungkinan lokasi file konfigurasi
    val possiblePaths = listOf(
        "$MODULE_DIR/kuro_mirror_high.cfg",
        "/data/local/tmp/kuro_mirror_high.cfg",
        "/storage/emulated/0/Android/data/com.bandainamcoent.bleach_mh_ww/files/kuro_mirror_high.cfg"
    )

    fun getActiveConfigPath(): String? {
        if (!RootUtil.isRootAvailable()) return null
        for (path in possiblePaths) {
            val check = RootUtil.execute("if [ -f $path ]; then echo 'exists'; fi")
            if (check == "exists") return path
        }
        return null
    }

    fun isModuleInstalled(): Boolean {
        val check = RootUtil.execute("if [ -d $MODULE_DIR ]; then echo 'yes'; fi")
        return check == "yes"
    }

    fun getModuleProp(key: String): String {
        val output = RootUtil.execute("grep '^$key=' $MODULE_DIR/module.prop | cut -d'=' -f2")
        return output.ifEmpty { "Unknown" }
    }

    fun readConfig(): ModuleConfig {
        val path = getActiveConfigPath() ?: return ModuleConfig()
        val content = RootUtil.execute("cat $path")
        val config = ModuleConfig()

        content.lines().forEach { line ->
            val parts = line.split("=")
            if (parts.size == 2) {
                val key = parts[0].trim()
                val value = parts[1].trim()
                when (key) {
                    "unity_speed" -> config.unitySpeed = value.toFloatOrNull() ?: 1.0f
                    "damage_rate" -> config.damageRate = value.toFloatOrNull() ?: 1.0f
                    "god_mode" -> config.godMode = value.toIntOrNull() ?: 0
                    "dump" -> config.dump = value.toIntOrNull() ?: 0
                    "max_damage_rate" -> config.maxDamageRate = value.toFloatOrNull() ?: 10.0f
                    "max_unity_speed" -> config.maxUnitySpeed = value.toFloatOrNull() ?: 5.0f
                }
            }
        }
        return config
    }

    fun writeConfig(config: ModuleConfig): Boolean {
        val path = getActiveConfigPath() ?: "$MODULE_DIR/kuro_mirror_high.cfg" // Default path if none exists
        
        val content = """
            unity_speed=${String.format("%.1f", config.unitySpeed)}
            damage_rate=${String.format("%.1f", config.damageRate)}
            god_mode=${config.godMode}
            dump=${config.dump}
            max_damage_rate=${String.format("%.1f", config.maxDamageRate)}
            max_unity_speed=${String.format("%.1f", config.maxUnitySpeed)}
        """.trimIndent()

        return try {
            RootUtil.execute("echo '$content' > $path")
            RootUtil.execute("chmod 644 $path")
            true
        } catch (e: Exception) {
            false
        }
    }
}
