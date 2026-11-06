package com.yogeshpaliyal.deepr.ui.screens.sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.yogeshpaliyal.deepr.ui.resources.Res
import com.yogeshpaliyal.deepr.ui.resources.device_sync_failed
import com.yogeshpaliyal.deepr.ui.resources.invite_expired
import com.yogeshpaliyal.deepr.ui.resources.invite_failed
import com.yogeshpaliyal.deepr.ui.resources.invite_invalid
import com.yogeshpaliyal.deepr.ui.resources.join_failed
import com.yogeshpaliyal.deepr.ui.resources.max_peers_reached
import com.yogeshpaliyal.deepr.ui.resources.operation_failed
import com.yogeshpaliyal.deepr.ui.resources.profile_already_shared
import org.jetbrains.compose.resources.stringResource

/**
 * Maps a [com.yogeshpaliyal.deepr.viewmodel.SyncViewModel] message key to a localized string.
 * Unknown keys fall back to a generic error, never to raw exception text.
 */
@Composable
internal fun rememberSyncMessageResolver(): (String) -> String {
    val operationFailed = stringResource(Res.string.operation_failed)
    val messages =
        mapOf(
            "invite_expired" to stringResource(Res.string.invite_expired),
            "invite_invalid" to stringResource(Res.string.invite_invalid),
            "invite_failed" to stringResource(Res.string.invite_failed),
            "profile_already_shared" to stringResource(Res.string.profile_already_shared),
            "max_peers_reached" to stringResource(Res.string.max_peers_reached),
            "sync_failed" to stringResource(Res.string.device_sync_failed),
            "join_failed" to stringResource(Res.string.join_failed),
        )
    return remember(messages, operationFailed) { { key -> messages[key] ?: operationFailed } }
}
