package com.yogeshpaliyal.deepr.ui.screens.sync

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yogeshpaliyal.deepr.Profile
import com.yogeshpaliyal.deepr.ui.LocalNavigator
import com.yogeshpaliyal.deepr.ui.Screen
import com.yogeshpaliyal.deepr.ui.screens.home.rememberCopyLinkAction
import com.yogeshpaliyal.deepr.ui.resources.Res
import com.yogeshpaliyal.deepr.ui.resources.back
import com.yogeshpaliyal.deepr.ui.resources.cancel
import com.yogeshpaliyal.deepr.ui.resources.confirm
import com.yogeshpaliyal.deepr.ui.resources.confirm_pairing_message
import com.yogeshpaliyal.deepr.ui.resources.confirm_pairing_title
import com.yogeshpaliyal.deepr.ui.resources.copied_to_clipboard
import com.yogeshpaliyal.deepr.ui.resources.copy_invite_code
import com.yogeshpaliyal.deepr.ui.resources.invite_code
import com.yogeshpaliyal.deepr.ui.resources.invite_expired_label
import com.yogeshpaliyal.deepr.ui.resources.invite_expires_in
import com.yogeshpaliyal.deepr.ui.resources.invite_keep_open_hint
import com.yogeshpaliyal.deepr.ui.resources.reject
import com.yogeshpaliyal.deepr.ui.resources.select_profile_to_share
import com.yogeshpaliyal.deepr.ui.resources.share_profile_title
import com.yogeshpaliyal.deepr.viewmodel.AccountViewModel
import com.yogeshpaliyal.deepr.viewmodel.InviteState
import com.yogeshpaliyal.deepr.viewmodel.SyncViewModel
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.Check
import compose.icons.tablericons.Copy
import compose.icons.tablericons.Share
import compose.icons.tablericons.User
import io.github.alexzhirkevich.qrose.options.QrBrush
import io.github.alexzhirkevich.qrose.options.QrPixelShape
import io.github.alexzhirkevich.qrose.options.circle
import io.github.alexzhirkevich.qrose.options.solid
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock

data class ShareProfile(
    val profileId: Long? = null,
) : Screen {
    @Composable
    override fun Content() {
        ShareProfileScreen(initialProfileId = profileId)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareProfileScreen(
    initialProfileId: Long? = null,
    modifier: Modifier = Modifier,
    viewModel: SyncViewModel = koinViewModel(),
    accountViewModel: AccountViewModel = koinViewModel(),
) {
    val navigator = LocalNavigator.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val invite by viewModel.invite.collectAsStateWithLifecycle()
    val pendingPairing by viewModel.pendingPairing.collectAsStateWithLifecycle()
    val profiles by accountViewModel.allProfiles.collectAsStateWithLifecycle()
    val currentProfile by accountViewModel.currentProfile.collectAsStateWithLifecycle()
    val copyLink = rememberCopyLinkAction()
    val copiedLabel = stringResource(Res.string.copied_to_clipboard)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.share_profile_title)) },
                navigationIcon = {
                    IconButton(onClick = { navigator.removeLast() }) {
                        Icon(
                            TablerIcons.ArrowLeft,
                            contentDescription = stringResource(Res.string.back),
                            modifier = if (isRtl) Modifier.scale(-1f, 1f) else Modifier,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val currentInvite = invite
            if (currentInvite == null) {
                Text(
                    text = stringResource(Res.string.select_profile_to_share),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
                val highlightedId = initialProfileId ?: currentProfile?.id
                profiles.forEach { profile ->
                    ProfilePickerRow(
                        profile = profile,
                        highlighted = profile.id == highlightedId,
                        onClick = { viewModel.startInvite(profile.id) },
                    )
                }
            } else {
                InviteContent(
                    invite = currentInvite,
                    onCopy = { copyLink(copiedLabel, currentInvite.code) },
                    onCancel = { viewModel.cancelInvite() },
                )
            }
        }
    }

    val pairing = pendingPairing
    if (pairing != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(Res.string.confirm_pairing_title)) },
            text = {
                Text(
                    stringResource(
                        Res.string.confirm_pairing_message,
                        pairing.peerName,
                        formatSas(pairing.sas),
                    ),
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmPairing() }) {
                    Text(stringResource(Res.string.confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.rejectPairing() }) {
                    Text(stringResource(Res.string.reject))
                }
            },
        )
    }
}

@Composable
private fun ProfilePickerRow(
    profile: Profile,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (highlighted) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
            ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = TablerIcons.User,
                contentDescription = null,
                tint =
                    if (highlighted) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            Text(
                text = profile.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (highlighted) {
                Icon(
                    imageVector = TablerIcons.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(
                imageVector = TablerIcons.Share,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InviteContent(
    invite: InviteState,
    onCopy: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var nowEpochSec by remember { mutableLongStateOf(Clock.System.now().epochSeconds) }
    LaunchedEffect(invite.expiresAtEpochSec) {
        while (true) {
            nowEpochSec = Clock.System.now().epochSeconds
            delay(1_000L)
        }
    }
    val remaining = (invite.expiresAtEpochSec - nowEpochSec).coerceAtLeast(0L)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = invite.profileName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )

        Box(
            modifier =
                Modifier
                    .size(240.dp)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .padding(12.dp),
        ) {
            Image(
                painter =
                    rememberQrCodePainter(data = invite.code) {
                        shapes { darkPixel = QrPixelShape.circle() }
                        colors { dark = QrBrush.solid(Color.Black) }
                    },
                contentDescription = stringResource(Res.string.invite_code),
                modifier = Modifier.fillMaxSize().aspectRatio(1f),
            )
        }

        Text(
            text = invite.code,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .padding(12.dp),
        )

        Button(onClick = onCopy) {
            Icon(imageVector = TablerIcons.Copy, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.copy_invite_code))
        }

        Text(
            text =
                if (remaining == 0L) {
                    stringResource(Res.string.invite_expired_label)
                } else {
                    stringResource(Res.string.invite_expires_in, formatCountdown(remaining))
                },
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (remaining == 0L) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )

        Text(
            text = stringResource(Res.string.invite_keep_open_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.cancel))
        }
    }
}

internal fun formatSas(sas: String): String = sas.chunked(3).joinToString(" ")

private fun formatCountdown(totalSeconds: Long): String {
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}
