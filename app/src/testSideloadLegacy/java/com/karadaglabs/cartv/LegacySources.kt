package com.karadaglabs.cartv

import java.io.File

/**
 * sideloadLegacy testleri: kaynak önce app/src/sideloadLegacy altında,
 * yoksa app/src/main altında aranır.
 */
object LegacySources {

    fun flavorPath(dir: File, base: String, relative: String): String {
        val legacy = "$base/sideloadLegacy/$relative"
        return if (File(dir, legacy).exists()) legacy else "$base/main/$relative"
    }
}
