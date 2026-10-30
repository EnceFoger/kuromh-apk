package com.kuromh.controller.core

data class ModuleConfig(
    val unitySpeed: Float = 1.0f,
    val damageRate: Float = 1.0f,
    val godMode: Boolean = false,
    val dump: Boolean = false,
    val maxDamageRate: Float = 10.0f,
    val maxUnitySpeed: Float = 5.0f
)
