package com.yogeshpaliyal.deepr.ui.screens.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yogeshpaliyal.deepr.ui.LocalNavigator
import com.yogeshpaliyal.deepr.ui.Screen
import com.yogeshpaliyal.deepr.ui.components.SettingsSection
import com.yogeshpaliyal.deepr.ui.platform.rememberShowToast
import com.yogeshpaliyal.deepr.ui.resources.Res
import com.yogeshpaliyal.deepr.ui.resources.back
import com.yogeshpaliyal.deepr.ui.resources.cancel
import com.yogeshpaliyal.deepr.ui.resources.confirm
import com.yogeshpaliyal.deepr.ui.resources.device_name
import com.yogeshpaliyal.deepr.ui.resources.device_sync
import com.yogeshpaliyal.deepr.ui.resources.join_a_shared_profile
import com.yogeshpaliyal.deepr.ui.resources.last_sync_days
import com.yogeshpaliyal.deepr.ui.resources.last_sync_error
import com.yogeshpaliyal.deepr.ui.resources.last_sync_hours
import com.yogeshpaliyal.deepr.ui.resources.last_sync_just_now
import com.yogeshpaliyal.deepr.ui.resources.last_sync_minutes
import com.yogeshpaliyal.deepr.ui.resources.last_sync_never
import com.yogeshpaliyal.deepr.ui.resources.max_devices
import com.yogeshpaliyal.deepr.ui.resources.max_devices_description
import com.yogeshpaliyal.deepr.ui.resources.more_options
import com.yogeshpaliyal.deepr.ui.resources.needs_repair
import com.yogeshpaliyal.deepr.ui.resources.no_paired_devices
import com.yogeshpaliyal.deepr.ui.resources.no_paired_devices_description
import com.yogeshpaliyal.deepr.ui.resources.paired_devices
import com.yogeshpaliyal.deepr.ui.resources.remove_all_devices
import com.yogeshpaliyal.deepr.ui.resources.remove_all_devices_confirm_message
import com.yogeshpaliyal.deepr.ui.resources.remove_all_devices_confirm_title
import com.yogeshpaliyal.deepr.ui.resources.remove_device
import com.yogeshpaliyal.deepr.ui.resources.same_network_hint
import com.yogeshpaliyal.deepr.ui.resources.save
import com.yogeshpaliyal.deepr.ui.resources.share_a_profile
import com.yogeshpaliyal.deepr.ui.resources.sync_now
import com.yogeshpaliyal.deepr.ui.resources.unshare_profile
import com.yogeshpaliyal.deepr.viewmodel.PeerState
import com.yogeshpaliyal.deepr.viewmodel.SyncViewModel
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.Check
import compose.icons.tablericons.DotsVertical
import compose.icons.tablericons.Refresh
import compose.icons.tablericons.Share
import compose.icons.tablericons.Trash
import compose.icons.tablericons.User
import compose.icons.tablericons.Wifi
import kotlinx.coroutines.flow.collect
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock

object SyncSettings : Screen {
    @Composable
    override fun Content() {
        SyncSettingsScreen()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SyncViewModel = koinViewModel(),
) {
    val navigator = LocalNavigator.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val deviceName by viewModel.deviceName.collectAsStateWithLifecycle()
    val maxPeers by viewModel.maxPeers.collectAsStateWithLifecycle()
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val showToast = rememberShowToast()
    val resolveMessage = rememberSyncMessageResolver()

    var nameDraft by remember(deviceName) { mutableStateOf(deviceName) }
    var showRemoveAllDialog by remember { mutableStateOf(false) }
    val maxDevicesLabel = stringResource(Res.string.max_devices)

    LaunchedEffect(Unit) {
        viewModel.messages.collect { key -> showToast(resolveMessage(key)) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.device_sync)) },
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
        ) {
            // Same-network limitation hint
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = TablerIcons.Wifi,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = stringResource(Res.string.same_network_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            // Device name
            SettingsSection(stringResource(Res.string.device_name)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = nameDraft,
                        onValueChange = { nameDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            IconButton(
                                onClick = { viewModel.setDeviceName(nameDraft) },
                                enabled = nameDraft.isNotBlank() && nameDraft != deviceName,
                            ) {
                                Icon(
                                    imageVector = TablerIcons.Check,
                                    contentDescription = stringResource(Res.string.save),
                                )
                            }
                        },
                    )
                }
            }

            // Maximum devices
            SettingsSection(stringResource(Res.string.max_devices)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Text(
                        text = maxPeers.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Slider(
                        value = maxPeers.toFloat(),
                        onValueChange = { viewModel.setMaxPeers(it.toInt()) },
                        valueRange = 1f..10f,
                        steps = 8,
                        modifier = Modifier.semantics { contentDescription = maxDevicesLabel },
                    )
                    Text(
                        text = stringResource(Res.string.max_devices_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Actions
            Button(
                onClick = { navigator.add(ShareProfile()) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = TablerIcons.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.share_a_profile))
            }
            OutlinedButton(
                onClick = { navigator.add(JoinSharedProfile) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = TablerIcons.User, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.join_a_shared_profile))
            }

            // Paired devices
            SettingsSection(stringResource(Res.string.paired_devices)) {
                if (peers.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.no_paired_devices),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(Res.string.no_paired_devices_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        peers.forEach { peer ->
                            PeerRow(
                                peer = peer,
                                onSyncNow = { viewModel.syncNow(peer.id) },
                                onUnshare = { shareId -> viewModel.unshare(peer.id, shareId) },
                                onRemove = { viewModel.removePeer(peer.id) },
                            )
                        }
                    }
                }
            }

            if (peers.isNotEmpty()) {
                TextButton(
                    onClick = { showRemoveAllDialog = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(
                        text = stringResource(Res.string.remove_all_devices),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (showRemoveAllDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveAllDialog = false },
            title = { Text(stringResource(Res.string.remove_all_devices_confirm_title)) },
            text = { Text(stringResource(Res.string.remove_all_devices_confirm_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeAllPeers()
                        showRemoveAllDialog = false
                    },
                ) {
                    Text(stringResource(Res.string.confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRemoveAllDialog = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun PeerRow(
    peer: PeerState,
    onSyncNow: () -> Unit,
    onUnshare: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = peer.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (peer.needsRepair) {
                        RepairBadge()
                    }
                }
                if (peer.sharedProfiles.isNotEmpty()) {
                    Text(
                        text = peer.sharedProfiles.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = lastSyncLabel(peer.lastSyncAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (peer.lastError.isNotEmpty()) {
                    Text(
                        text = stringResource(Res.string.last_sync_error, peer.lastError),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = TablerIcons.DotsVertical,
                        contentDescription = stringResource(Res.string.more_options),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(TablerIcons.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(stringResource(Res.string.sync_now))
                            }
                        },
                        onClick = {
                            menuExpanded = false
                            onSyncNow()
                        },
                    )
                    peer.sharedShares.forEach { share ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(TablerIcons.AlertTriangle, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(stringResource(Res.string.unshare_profile, share.profileName))
                                }
                            },
                            onClick = {
                                menuExpanded = false
                                onUnshare(share.shareId)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    TablerIcons.Trash,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    stringResource(Res.string.remove_device),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                        onClick = {
                            menuExpanded = false
                            onRemove()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RepairBadge() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.needs_repair),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun lastSyncLabel(lastSyncAtMillis: Long): String {
    if (lastSyncAtMillis <= 0L) return stringResource(Res.string.last_sync_never)
    val diffSeconds = ((Clock.System.now().toEpochMilliseconds() - lastSyncAtMillis) / 1000L).coerceAtLeast(0L)
    return when {
        diffSeconds < 60L -> stringResource(Res.string.last_sync_just_now)
        diffSeconds < 3_600L -> stringResource(Res.string.last_sync_minutes, (diffSeconds / 60L).toInt())
        diffSeconds < 86_400L -> stringResource(Res.string.last_sync_hours, (diffSeconds / 3_600L).toInt())
        else -> stringResource(Res.string.last_sync_days, (diffSeconds / 86_400L).toInt())
    }
}
