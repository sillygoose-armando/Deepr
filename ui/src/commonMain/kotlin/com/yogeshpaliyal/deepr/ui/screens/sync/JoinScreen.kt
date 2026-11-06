package com.yogeshpaliyal.deepr.ui.screens.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yogeshpaliyal.deepr.p2p.node.JoinTarget
import com.yogeshpaliyal.deepr.ui.LocalNavigator
import com.yogeshpaliyal.deepr.ui.Screen
import com.yogeshpaliyal.deepr.ui.platform.rememberQrScanLauncher
import com.yogeshpaliyal.deepr.ui.resources.Res
import com.yogeshpaliyal.deepr.ui.resources.back
import com.yogeshpaliyal.deepr.ui.resources.check_code_matches
import com.yogeshpaliyal.deepr.ui.resources.done
import com.yogeshpaliyal.deepr.ui.resources.invite_code
import com.yogeshpaliyal.deepr.ui.resources.invite_code_hint
import com.yogeshpaliyal.deepr.ui.resources.inviter_profile
import com.yogeshpaliyal.deepr.ui.resources.join
import com.yogeshpaliyal.deepr.ui.resources.join_done
import com.yogeshpaliyal.deepr.ui.resources.join_done_description
import com.yogeshpaliyal.deepr.ui.resources.join_failed
import com.yogeshpaliyal.deepr.ui.resources.join_title
import com.yogeshpaliyal.deepr.ui.resources.joining
import com.yogeshpaliyal.deepr.ui.resources.merge_into_profile
import com.yogeshpaliyal.deepr.ui.resources.new_profile
import com.yogeshpaliyal.deepr.ui.resources.profile_name_label
import com.yogeshpaliyal.deepr.ui.resources.scan_qr
import com.yogeshpaliyal.deepr.ui.resources.waiting_for_confirmation
import com.yogeshpaliyal.deepr.viewmodel.AccountViewModel
import com.yogeshpaliyal.deepr.viewmodel.InvitePreview
import com.yogeshpaliyal.deepr.viewmodel.JoinState
import com.yogeshpaliyal.deepr.viewmodel.SyncViewModel
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.Check
import compose.icons.tablericons.Scan
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

object JoinSharedProfile : Screen {
    @Composable
    override fun Content() {
        JoinScreen()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinScreen(
    modifier: Modifier = Modifier,
    viewModel: SyncViewModel = koinViewModel(),
    accountViewModel: AccountViewModel = koinViewModel(),
) {
    val navigator = LocalNavigator.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val joinState by viewModel.join.collectAsStateWithLifecycle()
    val profiles by accountViewModel.allProfiles.collectAsStateWithLifecycle()
    val resolveMessage = rememberSyncMessageResolver()

    var code by remember { mutableStateOf("") }
    var useNewProfile by remember { mutableStateOf(true) }
    var newProfileName by remember { mutableStateOf("") }
    var mergeProfileId by remember { mutableStateOf<Long?>(null) }
    var mergeExpanded by remember { mutableStateOf(false) }

    val qrScannerLauncher =
        rememberQrScanLauncher { scanned ->
            code = scanned
        }

    val preview = remember(code) { if (code.isBlank()) null else viewModel.previewInvite(code.trim()) }

    LaunchedEffect(preview) {
        val valid = preview
        if (valid is InvitePreview.Valid && newProfileName.isBlank()) {
            newProfileName = valid.profileName
        }
    }

    val canJoin =
        preview is InvitePreview.Valid &&
            if (useNewProfile) {
                newProfileName.isNotBlank()
            } else {
                mergeProfileId != null
            }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.join_title)) },
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
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.invite_code)) },
                placeholder = { Text(stringResource(Res.string.invite_code_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                trailingIcon =
                    if (qrScannerLauncher != null) {
                        {
                            IconButton(onClick = qrScannerLauncher) {
                                Icon(
                                    imageVector = TablerIcons.Scan,
                                    contentDescription = stringResource(Res.string.scan_qr),
                                )
                            }
                        }
                    } else {
                        null
                    },
            )

            when (val result = preview) {
                is InvitePreview.Valid ->
                    Text(
                        text = stringResource(Res.string.inviter_profile, result.profileName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                is InvitePreview.Invalid ->
                    Text(
                        text = resolveMessage(result.messageKey),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                null -> Unit
            }

            // Target selection
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RadioButton(
                            selected = useNewProfile,
                            onClick = { useNewProfile = true },
                        )
                        Text(
                            text = stringResource(Res.string.new_profile),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    if (useNewProfile) {
                        OutlinedTextField(
                            value = newProfileName,
                            onValueChange = { newProfileName = it },
                            modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                            label = { Text(stringResource(Res.string.profile_name_label)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RadioButton(
                            selected = !useNewProfile,
                            onClick = { useNewProfile = false },
                        )
                        Text(
                            text = stringResource(Res.string.merge_into_profile),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    if (!useNewProfile) {
                        val selectedMergeProfile = profiles.firstOrNull { it.id == mergeProfileId }
                        ExposedDropdownMenuBox(
                            expanded = mergeExpanded,
                            onExpandedChange = { mergeExpanded = !mergeExpanded },
                            modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                        ) {
                            OutlinedTextField(
                                value = selectedMergeProfile?.name ?: "",
                                onValueChange = {},
                                readOnly = true,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = mergeExpanded)
                                },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                            )
                            ExposedDropdownMenu(
                                expanded = mergeExpanded,
                                onDismissRequest = { mergeExpanded = false },
                            ) {
                                profiles.forEach { profile ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                if (profile.id == mergeProfileId) {
                                                    Icon(
                                                        imageVector = TablerIcons.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp),
                                                        tint = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                                Text(profile.name)
                                            }
                                        },
                                        onClick = {
                                            mergeProfileId = profile.id
                                            mergeExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val target =
                        if (useNewProfile) {
                            JoinTarget.NewProfile(newProfileName.trim())
                        } else {
                            JoinTarget.MergeInto(mergeProfileId ?: 0L)
                        }
                    viewModel.join(code.trim(), target)
                },
                enabled = canJoin && joinState is JoinState.Idle,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.join))
            }

            JoinStatus(
                state = joinState,
                resolveMessage = resolveMessage,
                onDone = {
                    viewModel.resetJoin()
                    navigator.removeLast()
                },
            )
        }
    }
}

@Composable
private fun JoinStatus(
    state: JoinState,
    resolveMessage: (String) -> String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is JoinState.Idle -> Unit
        is JoinState.Connecting ->
            Row(
                modifier = modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Text(stringResource(Res.string.joining))
            }
        is JoinState.WaitingForConfirm -> {
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(Res.string.check_code_matches, state.peerName, formatSas(state.sas)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Text(
                    text = stringResource(Res.string.waiting_for_confirmation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        is JoinState.Done ->
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(Res.string.join_done),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(Res.string.join_done_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onDone) {
                    Text(stringResource(Res.string.done))
                }
            }
        is JoinState.Failed ->
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(Res.string.join_failed),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = resolveMessage(state.messageKey),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
    }
}
