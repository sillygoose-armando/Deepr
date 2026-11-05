package com.yogeshpaliyal.deepr.ui

import com.yogeshpaliyal.deepr.data.SilentSaveResult
import com.yogeshpaliyal.deepr.data.SilentSaver
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

/**
 * Entry point for the iOS Share Extension (DeeprShare).
 *
 * Called from Swift (ShareViewController.swift). The extension runs in its own process,
 * so Koin is initialised only once per process lifetime.
 *
 * @param dataDir  Path to the data directory (same as DeeprApp.swift).
 * @param text     The shared text (URL or plain text containing a URL).
 * @param title    Optional title provided by the sharing source.
 * @param onResult Callback with one of: "saved" | "exists" | "invalid" | "failed".
 */
fun saveSharedLink(dataDir: String, text: String, title: String?, onResult: (String) -> Unit) {
    // Start Koin only if it hasn't been started yet (standalone extension process)
    if (KoinPlatform.getKoinOrNull() == null) {
        initKoin(dataDir)
    }

    MainScope().launch {
        val saver = KoinPlatform.getKoin().get<SilentSaver>()
        val result = saver.save(text, title)
        val status = when (result) {
            is SilentSaveResult.Saved -> "saved"
            is SilentSaveResult.AlreadyExists -> "exists"
            is SilentSaveResult.InvalidLink -> "invalid"
            is SilentSaveResult.Failed -> "failed"
        }
        onResult(status)
    }
}
