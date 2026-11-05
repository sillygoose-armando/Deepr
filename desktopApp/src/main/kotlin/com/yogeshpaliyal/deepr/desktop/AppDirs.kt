package com.yogeshpaliyal.deepr.desktop

import java.io.File

fun appDataDir(): File {
    val os = System.getProperty("os.name").lowercase()
    val dir =
        when {
            os.contains("win") ->
                File(System.getenv("APPDATA"), "Deepr")
            os.contains("mac") ->
                File(System.getProperty("user.home"), "Library/Application Support/Deepr")
            else -> {
                val xdg = System.getenv("XDG_DATA_HOME")
                if (!xdg.isNullOrBlank()) {
                    File(xdg, "deepr")
                } else {
                    File(System.getProperty("user.home"), ".local/share/deepr")
                }
            }
        }
    dir.mkdirs()
    return dir
}
