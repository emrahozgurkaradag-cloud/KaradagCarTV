package com.karadaglabs.cartv

import java.io.File

/**
 * projection testleri: kaynak önce app/src/projection altında,
 * yoksa app/src/main altında aranır.
 */
object LegacySources {

    fun flavorPath(dir: File, base: String, relative: String): String {
        val legacy = "$base/projection/$relative"
        return if (File(dir, legacy).exists()) legacy else "$base/main/$relative"
    }
}
