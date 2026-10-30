package com.kuromh.controller.core

import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

/**
 * Optional interoperability contract for modules that expose a flat key=value config.
 * Does not infer hidden hooks or mod-specific behavior from a ZIP.
 */
object ExternalModControls {
    data class Control(
        val key: String,
        val label: String,
        val type: String,
        val value: String,
        val min: Float = 0f,
        val max: Float = 1f,
        val step: Float = 0.1f,
        val on: String = "1",
        val off: String = "0"
    )

    data class Panel(
        val moduleId: String,
        val title: String,
        val configFile: String,
        val controls: List<Control>
    )

    data class LoadResult(val panel: Panel?, val message: String)
    data class SaveResult(val ok: Boolean, val message: String)

    private val moduleIdPattern = Regex("[A-Za-z][A-Za-z0-9._-]{0,127}")
    private val keyPattern = Regex("[A-Za-z][A-Za-z0-9_]{0,63}")
    // Config stays inside the module directory; never accept absolute paths or ../.
    private val configNamePattern = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}\\.(cfg|conf|ini|txt)")
    private val booleanPattern = Regex("[A-Za-z0-9._-]{1,32}")

    private fun quote(text: String) = "'" + text.replace("'", "'\\''") + "'"
    private fun base(id: String) = "/data/adb/modules/" + id

    private fun readFile(path: String): String? {
        val output = RootUtil.execute(
            "if [ -f " + quote(path) + " ] && [ ! -L " + quote(path) + " ]; " +
                "then head -c 65537 " + quote(path) + "; fi"
        )
        if (output.isEmpty() || output.length > 65536) return null
        return output
    }

    /** List of controls is always declared by the installed module's own manifest. */
    fun load(moduleId: String): LoadResult {
        if (!moduleIdPattern.matches(moduleId) || moduleId == ModuleManager.MODULE_ID)
            return LoadResult(null, "Invalid external module ID.")
        if (!RootUtil.isRootAvailable()) return LoadResult(null, "Root permission is required.")

        val directory = base(moduleId)
        if (!RootUtil.execute("[ -f '$directory/module.prop' ] && echo yes").contains("yes"))
            return LoadResult(null, "Module is not installed.")

        val manifest = readFile("$directory/kuromh-controls.json")
            ?: return LoadResult(null, "This mod has no KUROMH feature-control manifest. ON/OFF is available, but individual features need integration.")

        return try {
            val json = JSONObject(manifest)
            val configFile = json.optString("config").trim()
            if (!configNamePattern.matches(configFile))
                return LoadResult(null, "Unsupported config path in mod manifest (use a .cfg/.conf/.ini/.txt file in module root).")

            val config = readFile("$directory/$configFile")
                ?: return LoadResult(null, "Mod config not found. This module must create $configFile before controls work.")
            val values = config.lineSequence()
                .filterNot { it.trimStart().startsWith("#") }
                .mapNotNull { line ->
                    val body = line.substringBefore('#')
                    val index = body.indexOf('=')
                    if (index < 1) null else body.substring(0, index).trim() to body.substring(index + 1).trim()
                }.toMap()

            val arr = json.optJSONArray("controls")
                ?: return LoadResult(null, "No controls declared in mod manifest.")
            val controls = mutableListOf<Control>()
            val keys = mutableSetOf<String>()
            for (i in 0 until minOf(arr.length(), 24)) {
                val item = arr.optJSONObject(i) ?: continue
                val key = item.optString("key")
                val type = item.optString("type")
                if (!keyPattern.matches(key) || !keys.add(key)) continue
                if (type != "switch" && type != "slider") continue
                val label = item.optString("label", key).take(60)
                if (type == "switch") {
                    val on = item.optString("on", "1")
                    val off = item.optString("off", "0")
                    if (!booleanPattern.matches(on) || !booleanPattern.matches(off) || on == off) continue
                    controls.add(Control(key, label, type, values[key] ?: off, on = on, off = off))
                } else {
                    val min = item.optDouble("min", 0.0).toFloat()
                    val max = item.optDouble("max", 10.0).toFloat()
                    val step = item.optDouble("step", 0.1).toFloat()
                    if (!min.isFinite() || !max.isFinite() || !step.isFinite() ||
                        min >= max || step <= 0f || max - min > 100000f
                    ) continue
                    controls.add(Control(key, label, type, values[key] ?: min.toString(), min, max, step))
                }
            }
            if (controls.isEmpty()) LoadResult(null, "No valid controls in mod manifest.")
            else LoadResult(Panel(moduleId, json.optString("title", moduleId).take(60), configFile, controls), "")
        } catch (_: Exception) {
            LoadResult(null, "Invalid mod control manifest.")
        }
    }

    /** Updates only a declared key inside that module's own existing config file. */
    fun save(moduleId: String, key: String, proposed: String): SaveResult {
        val panel = load(moduleId).panel ?: return SaveResult(false, "Module controls unavailable.")
        val control = panel.controls.firstOrNull { it.key == key }
            ?: return SaveResult(false, "This control is not declared by the module.")
        val value = if (control.type == "switch") {
            if (proposed != control.on && proposed != control.off)
                return SaveResult(false, "Invalid switch value.")
            proposed
        } else {
            val number = proposed.toFloatOrNull()
                ?: return SaveResult(false, "Invalid numeric value.")
            if (!number.isFinite() || number < control.min || number > control.max)
                return SaveResult(false, "Value is outside this mod's allowed range.")
            val offset = (number - control.min) / control.step
            if (abs(offset - kotlin.math.round(offset)) > 0.02f)
                return SaveResult(false, "Value does not match slider step.")
            String.format(Locale.US, "%.4f", number).trimEnd('0').trimEnd('.')
        }

        val file = base(moduleId) + "/" + panel.configFile
        val original = readFile(file) ?: return SaveResult(false, "Cannot read mod config.")
        var replaced = false
        val newLines = original.lines().map { line ->
            val index = line.substringBefore('#').indexOf('=')
            if (index < 1 || line.substring(0, index).trim() != key) line
            else {
                replaced = true
                key + "=" + value + if (line.contains('#')) " #" + line.substringAfter('#') else ""
            }
        }.toMutableList()
        if (!replaced) newLines.add(key + "=" + value)
        val newText = newLines.joinToString("\n").trimEnd() + "\n"
        val result = RootUtil.execute(
            "if [ -f " + quote(file) + " ] && [ ! -L " + quote(file) + " ]; " +
                "then printf '%s' " + quote(newText) + " > " + quote(file) + " && echo KUROMH_SAVED; fi"
        )
        if (!result.contains("KUROMH_SAVED")) return SaveResult(false, "Failed to write mod config.")
        // Do not claim the live hook changed; a module may only reload on restart/reboot.
        return SaveResult(true, "Setting saved. Restart the mod/game if it does not reload automatically.")
    }
}
