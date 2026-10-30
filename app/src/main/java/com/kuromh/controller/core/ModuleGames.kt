package com.kuromh.controller.core

import android.content.Context
import android.content.Intent

/** Game launch targets obtained from the supplied module binaries or package.txt. */
object ModuleGames {
    data class Game(
        val packageName: String = "",
        val displayName: String = "Game not identified",
        val installed: Boolean = false,
        val launchable: Boolean = false
    )

    private val packagePattern = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
    private val known = mapOf(
        "kuro_companion" to ("com.klab.bleach" to "Bleach: Brave Souls"),
        "zygisk-zeromodzbb" to ("com.klab.bleach" to "Bleach: Brave Souls")
    )

    /** Always call on an IO/background thread when root is enabled. */
    fun detect(context: Context, moduleId: String, allowRoot: Boolean): Game {
        val target = when {
            moduleId == ModuleManager.MODULE_ID -> {
                val pkg = if (allowRoot) RootUtil.execute(
                    "grep -v '^#' '/data/adb/modules/kuro_mirror_high/package.txt' | head -n 1"
                ).trim().takeIf { packagePattern.matches(it) }
                    else null
                val targetPackage = pkg ?: "com.bandainamcoent.bleach_mh_ww"
                targetPackage to "Kuro Mirror High game"
            }
            known.containsKey(moduleId) -> known.getValue(moduleId)
            else -> {
                if (!allowRoot) return Game()
                if (!Regex("[A-Za-z][A-Za-z0-9._-]{0,127}").matches(moduleId)) return Game()
                val pkg = RootUtil.execute(
                    "if [ -f '/data/adb/modules/$moduleId/package.txt' ]; then " +
                        "grep -v '^#' '/data/adb/modules/$moduleId/package.txt' | head -n 1; fi"
                ).trim().takeIf { packagePattern.matches(it) } ?: return Game()
                pkg to "Target game"
            }
        }
        val installed = try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(target.first, 0)
            true
        } catch (_: Exception) { false }
        val launchable = installed &&
            context.packageManager.getLaunchIntentForPackage(target.first) != null
        return Game(target.first, target.second, installed, launchable)
    }

    fun launch(context: Context, game: Game): Boolean {
        if (!game.installed || !game.launchable || game.packageName.isEmpty()) return false
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(game.packageName)
                ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) { false }
    }
}
