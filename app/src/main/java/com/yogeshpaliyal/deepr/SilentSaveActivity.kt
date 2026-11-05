package com.yogeshpaliyal.deepr

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.yogeshpaliyal.deepr.data.SilentSaveResult
import com.yogeshpaliyal.deepr.data.SilentSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

/**
 * Activity that silently saves shared links without opening the main app UI.
 * This provides a quick "save and go" experience for users who want to bookmark
 * links without interrupting their workflow.
 */
class SilentSaveActivity : ComponentActivity() {
    private val silentSaver: SilentSaver by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Store application context and scope for use after activity finishes
        val appContext = applicationContext

        // Handle the shared link
        handleSharedLink(intent, appContext)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val appContext = applicationContext
        handleSharedLink(intent, appContext)
    }

    /**
     * Processes the incoming share intent.
     */
    private fun handleSharedLink(
        intent: Intent,
        appContext: Context,
    ) {
        when {
            intent.action == Intent.ACTION_SEND && intent.type == "text/plain" -> {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                val title = intent.getStringExtra(Intent.EXTRA_TITLE)

                if (sharedText != null) {
                    // Launch in application scope so it continues after activity finishes
                    lifecycleScope.launch {
                        val result = silentSaver.save(sharedText, title)
                        handleResult(result, appContext, sharedText)
                    }
                }
            }
        }
    }

    private fun handleResult(
        result: SilentSaveResult,
        appContext: Context,
        link: String,
    ) {
        when (result) {
            is SilentSaveResult.Saved -> {
                showToast(appContext, appContext.getString(R.string.link_saved_silently))
            }
            is SilentSaveResult.AlreadyExists -> {
                showToast(appContext, appContext.getString(R.string.link_already_exists))
            }
            is SilentSaveResult.InvalidLink -> {
                showToast(appContext, appContext.getString(R.string.invalid_link_silent_save))
            }
            is SilentSaveResult.Failed -> {
                Log.e(TAG, "Failed to save link silently: $link", result.error)
                showToast(appContext, appContext.getString(R.string.failed_to_save_link))
            }
        }
    }

    /**
     * Helper to show toast messages and finish the activity.
     */
    private fun showToast(
        context: Context,
        message: String,
    ) {
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }

    companion object {
        private const val TAG = "SilentSaveActivity"
    }
}
