package com.kuromh.controller.core

import java.io.File
import java.util.zip.ZipFile

/**
 * Manages additional root-manager modules independently of KUROMH's own module.
 * Installation is delegated to the active root manager (no unsafe manual unpack).
 */
object OtherModuleManager {
    data class Module(val id: String, val name: String, val version: String, val enabled: Boolean)
    data class Result(val ok: Boolean, val message: String)

    private val idPattern = Regex("[A-Za-z][A-Za-z0-9._-]{0,127}")
    private val moduleRoot = "/data/adb/modules"
    private val staged = "/data/local/tmp/kuromh_other_module.zip"

    private fun safeId(id: String) = idPattern.matches(id) && id != "." && id != ".."

    private fun shellQuote(value: String) = "'" + value.replace("'", "'\\''") + "'"

    private fun moduleId(zip: File): String? = try {
        ZipFile(zip).use { archive ->
            val entry = archive.getEntry("module.prop") ?: return@use null
            archive.getInputStream(entry).bufferedReader().useLines { lines ->
                lines.firstOrNull { it.trimStart().startsWith("id=") }
                    ?.trim()?.substringAfter("=")?.trim()
                    ?.takeIf { safeId(it) }
            }
        }
    } catch (_: Exception) { null }

    fun install(zip: File): Result {
        val id = moduleId(zip) ?: return Result(false, "Invalid module ZIP: module.prop/id missing or invalid.")
        if (id == ModuleManager.MODULE_ID)
            return Result(false, "Use the KUROMH installer for the built-in module.")
        if (!RootUtil.isRootAvailable()) return Result(false, "Root access is required.")
        val target = "$moduleRoot/$id"
        val update = "/data/adb/modules_update/$id"
        // Existing directory may contain a previously installed module. Never delete it as fallback.
        val stagedOk = RootUtil.execute(
            "cp ${shellQuote(zip.absolutePath)} '$staged' && chmod 600 '$staged' && echo KURO_STAGED"
        ).lineSequence().any { it.trim() == "KURO_STAGED" }
        if (!stagedOk) return Result(false, "Cannot stage ZIP with root.")
        return try {
            val output = RootUtil.execute(
                "if command -v magisk >/dev/null 2>&1; then magisk --install-module '$staged'; " +
                "elif command -v ksud >/dev/null 2>&1; then ksud module install '$staged'; " +
                "elif command -v apd >/dev/null 2>&1; then apd module install '$staged'; " +
                "else echo KURO_NO_MANAGER; fi"
            )
            if (output.contains("KURO_NO_MANAGER")) {
                Result(false, "No supported Magisk/KernelSU/APatch module installer found.")
            } else {
                val found = RootUtil.execute(
                    "if [ -f '$target/module.prop' ] || [ -f '$update/module.prop' ]; then echo KURO_OK; fi"
                ).contains("KURO_OK")
                if (found) Result(true, "Module $id installed. Reboot to apply.")
                else Result(false, "Module installation not confirmed. Check root-manager logs.")
            }
        } finally {
            RootUtil.execute("rm -f '$staged'")
        }
    }

    fun list(): List<Module> {
        if (!RootUtil.isRootAvailable()) return emptyList()
        // Each line is a single module directory name. Never evaluate IDs as shell syntax.
        val output = RootUtil.execute(
            "for d in /data/adb/modules/*; do [ -f \"\$d/module.prop\" ] || continue; basename \"\$d\"; done"
        )
        return output.lineSequence().map { it.trim() }.filter { safeId(it) }
            .filter { it != ModuleManager.MODULE_ID }
            .mapNotNull { id ->
                val dir = "$moduleRoot/$id"
                val enabled = RootUtil.execute("[ ! -e '$dir/disable' ] && echo KURO_YES").contains("KURO_YES")
                val props = RootUtil.execute("cat '$dir/module.prop'")
                val values = props.lineSequence().mapNotNull {
                    val i = it.indexOf('=')
                    if (i <= 0) null else it.substring(0, i).trim() to it.substring(i + 1).trim()
                }.toMap()
                Module(id, values["name"] ?: id, values["version"] ?: "?", enabled)
            }.toList()
    }

    fun setEnabled(id: String, enabled: Boolean): Result {
        if (!safeId(id) || id == ModuleManager.MODULE_ID) return Result(false, "Invalid module ID.")
        if (!RootUtil.isRootAvailable()) return Result(false, "Root access is required.")
        val dir = "$moduleRoot/$id"
        val command = if (enabled)
            "[ -f '$dir/module.prop' ] && rm -f '$dir/disable' && echo KURO_OK"
        else
            "[ -f '$dir/module.prop' ] && touch '$dir/disable' && echo KURO_OK"
        val ok = RootUtil.execute(command).contains("KURO_OK")
        return Result(ok, if (ok) "Module updated. Reboot to apply." else "Could not update module.")
    }

    fun uninstall(id: String): Result {
        if (!safeId(id) || id == ModuleManager.MODULE_ID) return Result(false, "Invalid module ID.")
        if (!RootUtil.isRootAvailable()) return Result(false, "Root access is required.")
        val dir = "$moduleRoot/$id"
        // Standard root-module removal: mark for deletion on next boot.
        val ok = RootUtil.execute(
            "[ -f '$dir/module.prop' ] && touch '$dir/remove' && echo KURO_OK"
        ).contains("KURO_OK")
        return Result(ok, if (ok) "Removal scheduled. Reboot to complete." else "Could not schedule removal.")
    }
}
