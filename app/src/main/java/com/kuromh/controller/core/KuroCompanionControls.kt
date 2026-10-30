package com.kuromh.controller.core

import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/**
 * Adapter for the supplied Kuro Companion v0.1 Zygisk module.
 *
 * Its binary reads kuro_companion.cfg from the Bleach Brave Souls app files
 * directory and re-reads config approximately every two seconds.
 * It does not read a kuromh-controls.json from the Magisk module directory.
 */
object KuroCompanionControls {
    private const val MODULE_ID = "kuro_companion"
    private const val PACKAGE = "com.klab.bleach"
    private const val CONFIG = "kuro_companion.cfg"
    private val paths = listOf(
        "/storage/emulated/0/Android/data/$PACKAGE/files/$CONFIG",
        "/data/media/0/Android/data/$PACKAGE/files/$CONFIG",
        "/data/data/$PACKAGE/files/$CONFIG"
    )
    private val sliders = listOf(
        "attack_speed" to "Attack Speed",
        "move_speed" to "Move Speed",
        "attack_range" to "Attack Range",
        "unity_speed" to "Unity Speed",
        "game_speed" to "Game Speed",
        "damage_rate" to "Damage Rate"
    )
    private val switches = listOf(
        "skill_no_cd" to "Skill No Cooldown (experimental)",
        "god_mode" to "God Mode (experimental)",
        "skip_story" to "Skip Story (experimental)",
        "event_points" to "Event Points (experimental)"
    )

    private fun quote(text: String): String = "'" + text.replace("'", "'\\''") + "'"

    private fun existingPaths(): List<String> = paths.filter { path ->
        RootUtil.execute("if [ -f " + quote(path) + " ] && [ ! -L " + quote(path) +
            " ]; then echo KUROMH_PRESENT; fi").contains("KUROMH_PRESENT")
    }

    private fun read(path: String): String? {
        val text = RootUtil.execute("head -c 65537 " + quote(path))
        return if (text.isBlank() || text.length > 65536) null else text
    }

    private fun parse(text: String): Map<String, String> =
        text.lineSequence()
            .filterNot { it.trimStart().startsWith("#") }
            .mapNotNull {
                val line = it.substringBefore('#')
                val index = line.indexOf('=')
                if (index < 1) null else line.substring(0, index).trim() to line.substring(index + 1).trim()
            }.toMap()

    fun load(): ExternalModControls.LoadResult {
        if (!RootUtil.isRootAvailable()) return ExternalModControls.LoadResult(null, "Root permission required.")
        if (!RootUtil.execute("[ -f '/data/adb/modules/$MODULE_ID/module.prop' ] && echo yes").contains("yes"))
            return ExternalModControls.LoadResult(null, "Kuro Companion is not installed.")

        val path = existingPaths().firstOrNull()
            ?: return ExternalModControls.LoadResult(null,
                "Launch Bleach Brave Souls once with Kuro Companion enabled; it creates kuro_companion.cfg in the game's files folder.")
        val content = read(path) ?: return ExternalModControls.LoadResult(null, "Cannot read Companion config.")
        val values = parse(content)
        val controls = mutableListOf<ExternalModControls.Control>()
        controls += ExternalModControls.Control(
            key = "stage", label = "Enable gameplay hooks (stage 3)", type = "switch",
            value = values["stage"] ?: "1", on = "3", off = "1"
        )
        sliders.forEach { (key, label) ->
            val limit = (values["max_$key"]?.toFloatOrNull() ?: 5.0f).coerceIn(1f, 100f)
            controls += ExternalModControls.Control(
                key = key, label = label, type = "slider",
                value = values[key] ?: "1.0", min = 1f, max = limit, step = 0.1f
            )
        }
        switches.forEach { (key, label) ->
            controls += ExternalModControls.Control(
                key = key, label = label, type = "switch",
                value = values[key] ?: "0", on = "1", off = "0"
            )
        }
        return ExternalModControls.LoadResult(
            ExternalModControls.Panel(MODULE_ID, "Kuro Companion", path, controls), ""
        )
    }

    fun save(key: String, proposed: String): ExternalModControls.SaveResult {
        val result = load()
        val control = result.panel?.controls?.firstOrNull { it.key == key }
            ?: return ExternalModControls.SaveResult(false, result.message.ifEmpty { "Unknown feature." })
        val value = if (control.type == "switch") {
            if (proposed != control.on && proposed != control.off)
                return ExternalModControls.SaveResult(false, "Invalid switch value.")
            proposed
        } else {
            val number = proposed.toFloatOrNull()
                ?: return ExternalModControls.SaveResult(false, "Not a number.")
            if (!number.isFinite() || number < control.min || number > control.max)
                return ExternalModControls.SaveResult(false, "Value outside the supported range.")
            val steps = (number - control.min) / control.step
            if (abs(steps - round(steps)) > 0.02f)
                return ExternalModControls.SaveResult(false, "Invalid step.")
            String.format(Locale.US, "%.2f", number)
        }
        val files = existingPaths()
        if (files.isEmpty()) return ExternalModControls.SaveResult(false, "Companion config not found.")
        for (path in files) {
            val before = read(path) ?: return ExternalModControls.SaveResult(false, "Cannot read config.")
            var found = false
            val lines = before.lines().map { line ->
                val head = line.substringBefore('#')
                val index = head.indexOf('=')
                if (index < 1 || head.substring(0, index).trim() != key) line
                else {
                    found = true
                    "$key=$value" + if (line.contains('#')) " #"+line.substringAfter('#') else ""
                }
            }.toMutableList()
            if (!found) lines.add("$key=$value")
            val updated = lines.joinToString("\n").trimEnd() + "\n"
            val output = RootUtil.execute("if [ -f " + quote(path) + " ] && [ ! -L " + quote(path) + " ]; " +
                "then printf '%s' " + quote(updated) + " > " + quote(path) + " && echo KUROMH_SAVED; fi")
            if (!output.contains("KUROMH_SAVED"))
                return ExternalModControls.SaveResult(false, "Failed to write Companion config.")
        }
        return ExternalModControls.SaveResult(true,
            "Config saved. Companion polls it periodically; features marked experimental may still be unavailable in this module build.")
    }
}
