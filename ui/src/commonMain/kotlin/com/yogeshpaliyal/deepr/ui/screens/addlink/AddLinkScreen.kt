package com.yogeshpaliyal.deepr.ui.screens.addlink

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.yogeshpaliyal.deepr.GetLinksAndTags
import com.yogeshpaliyal.deepr.Tags
import com.yogeshpaliyal.deepr.ui.LocalNavigator
import com.yogeshpaliyal.deepr.ui.components.ClearInputIconButton
import com.yogeshpaliyal.deepr.ui.platform.rememberOpenLink
import com.yogeshpaliyal.deepr.ui.platform.rememberQrScanLauncher
import com.yogeshpaliyal.deepr.ui.platform.rememberShowToast
import com.yogeshpaliyal.deepr.ui.resources.Res
import com.yogeshpaliyal.deepr.ui.resources.add_tag
import com.yogeshpaliyal.deepr.ui.resources.back
import com.yogeshpaliyal.deepr.ui.resources.cancel
import com.yogeshpaliyal.deepr.ui.resources.create
import com.yogeshpaliyal.deepr.ui.resources.create_tag
import com.yogeshpaliyal.deepr.ui.resources.create_profile
import com.yogeshpaliyal.deepr.ui.resources.deeplink_already_exists
import com.yogeshpaliyal.deepr.ui.resources.edit
import com.yogeshpaliyal.deepr.ui.resources.enter_deeplink_command
import com.yogeshpaliyal.deepr.ui.resources.enter_link_name
import com.yogeshpaliyal.deepr.ui.resources.enter_link_name_error
import com.yogeshpaliyal.deepr.ui.resources.enter_notes
import com.yogeshpaliyal.deepr.ui.resources.execute
import com.yogeshpaliyal.deepr.ui.resources.failed_to_fetch_metadata
import com.yogeshpaliyal.deepr.ui.resources.fetch_name_from_link
import com.yogeshpaliyal.deepr.ui.resources.invalid_empty_deeplink
import com.yogeshpaliyal.deepr.ui.resources.profile
import com.yogeshpaliyal.deepr.ui.resources.profile_created_successfully
import com.yogeshpaliyal.deepr.ui.resources.profile_name
import com.yogeshpaliyal.deepr.ui.resources.profile_name_cannot_be_blank
import com.yogeshpaliyal.deepr.ui.resources.profile_name_exists
import com.yogeshpaliyal.deepr.ui.resources.qr_scanner
import com.yogeshpaliyal.deepr.ui.resources.remove_tag
import com.yogeshpaliyal.deepr.ui.resources.remove_thumbnail
import com.yogeshpaliyal.deepr.ui.resources.save
import com.yogeshpaliyal.deepr.ui.resources.save_and_execute
import com.yogeshpaliyal.deepr.ui.resources.select_profile
import com.yogeshpaliyal.deepr.ui.resources.tags
import com.yogeshpaliyal.deepr.ui.resources.tags_label
import com.yogeshpaliyal.deepr.ui.resources.thumbnail_url
import com.yogeshpaliyal.deepr.util.isValidDeeplink
import com.yogeshpaliyal.deepr.util.normalizeLink
import com.yogeshpaliyal.deepr.viewmodel.AccountViewModel
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.Check
import compose.icons.tablericons.Download
import compose.icons.tablericons.Link
import compose.icons.tablericons.Note
import compose.icons.tablericons.Photo
import compose.icons.tablericons.Plus
import compose.icons.tablericons.Qrcode
import compose.icons.tablericons.Tag
import compose.icons.tablericons.User
import compose.icons.tablericons.X
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLinkScreen(
    selectedLink: GetLinksAndTags,
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = koinInject(),
) {
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val showToast = rememberShowToast()
    val openLink = rememberOpenLink()
    val fetchMetadataErrorText = stringResource(Res.string.failed_to_fetch_metadata)
    val removeTagText = stringResource(Res.string.remove_tag)
    val deeplinkExistsText = stringResource(Res.string.deeplink_already_exists)
    val profileNameCannotBeBlank = stringResource(Res.string.profile_name_cannot_be_blank)
    val profileNameExists = stringResource(Res.string.profile_name_exists)
    val profileCreatedSuccessfully = stringResource(Res.string.profile_created_successfully)
    var deeprInfo by remember(selectedLink) {
        mutableStateOf(
            selectedLink,
        )
    }
    var isError by remember { mutableStateOf(false) }
    var isNameError by remember { mutableStateOf(false) }
    var isFetchingMetadata by remember { mutableStateOf(false) }

    val qrScannerLauncher =
        rememberQrScanLauncher { contents ->
            val normalizedLink = normalizeLink(contents)
            deeprInfo = deeprInfo.copy(link = normalizedLink)
            isError = false
        }

    // Tags
    var newTagName by remember { mutableStateOf("") }
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()
    val selectedTags = remember { mutableStateListOf<Tags>() }
    val initialSelectedTags = remember { mutableStateListOf<Tags>() }
    val isThumbnailEnable by viewModel.isThumbnailEnable.collectAsStateWithLifecycle()
    val isCreate = selectedLink.id == 0L

    // Profile selection
    val allProfiles by viewModel.allProfiles.collectAsStateWithLifecycle()
    val currentProfile by viewModel.currentProfile.collectAsStateWithLifecycle()
    var selectedProfileId by remember(selectedLink) {
        mutableStateOf(selectedLink.profileId)
    }

    // Pre-populate with current profile if it's a new link and we are currently at default
    // This handles cases where the current profile might take a moment to load
    LaunchedEffect(currentProfile) {
        if (isCreate && selectedProfileId == 1L && currentProfile != null) {
            selectedProfileId = currentProfile!!.id
        }
    }

    var showCreateProfileDialog by remember { mutableStateOf(false) }
    var pendingProfileNameToSelect by remember { mutableStateOf<String?>(null) }

    val fetchMetadata: () -> Unit = {
        isFetchingMetadata = true
        viewModel.fetchMetaData(deeprInfo.link) {
            isFetchingMetadata = false
            if (it != null) {
                deeprInfo = deeprInfo.copy(name = it.title ?: "", thumbnail = it.image ?: "")
                isNameError = false
            } else {
                showToast(fetchMetadataErrorText)
            }
        }
    }

    LaunchedEffect(selectedLink) {
        if (isValidDeeplink(selectedLink.link) && selectedLink.name.isEmpty()) {
            fetchMetadata()
        }
    }

    // Initialize selected tags if in edit mode
    LaunchedEffect(isCreate) {
        if (isCreate.not()) {
            val existingTags =
                selectedLink.tagsIds?.split(",")?.mapIndexed { index, tagId ->
                    Tags(
                        tagId.trim().toLong(),
                        selectedLink.tagsNames
                            ?.split(",")
                            ?.getOrNull(index)
                            ?.trim() ?: "Unknown",
                    )
                }
            selectedTags.clear()
            initialSelectedTags.clear()
            existingTags?.toList()?.let {
                selectedTags.addAll(it)
                initialSelectedTags.addAll(it)
            }
        }
    }

    val save: (executeAfterSave: Boolean) -> Unit = { executeAfterSave ->
        scope.launch {
            // Normalize the link before saving
            val normalizedLink = normalizeLink(deeprInfo.link)

            if (isCreate && viewModel.isLinkDuplicate(normalizedLink)) {
                showToast(deeplinkExistsText)
                return@launch
            }

            // Remove unselected tags
            val initialTagIds = initialSelectedTags.map { it.id }.toSet()
            val currentTagIds = selectedTags.map { it.id }.toSet()
            val tagsToRemove = initialTagIds - currentTagIds
            tagsToRemove.forEach { tagId ->
                viewModel.removeTagFromLink(deeprInfo.id, tagId)
            }

            if (deeprInfo.id == 0L) {
                // New Account
                viewModel.insertAccount(
                    normalizedLink,
                    deeprInfo.name,
                    executeAfterSave,
                    selectedTags,
                    deeprInfo.notes,
                    deeprInfo.thumbnail,
                    selectedProfileId,
                )
            } else {
                // Edit
                viewModel.updateDeeplink(
                    deeprInfo.id,
                    normalizedLink,
                    deeprInfo.name,
                    selectedTags,
                    deeprInfo.notes,
                    deeprInfo.thumbnail,
                    selectedProfileId,
                )
            }
            if (executeAfterSave) {
                openLink(normalizedLink)
            }
            navigator.removeLast()
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = if (isCreate) stringResource(Res.string.create) else stringResource(Res.string.edit),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navigator.removeLast() }) {
                        Icon(TablerIcons.ArrowLeft, contentDescription = stringResource(Res.string.back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .fillMaxSize()
                    .imePadding(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Link Section
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = TablerIcons.Link,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(Res.string.enter_deeplink_command),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        OutlinedTextField(
                            value = deeprInfo.link,
                            onValueChange = {
                                deeprInfo = deeprInfo.copy(link = it)
                                isError = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("https://example.com or app://deeplink") },
                            isError = isError,
                            supportingText = {
                                if (isError) {
                                    Text(text = stringResource(Res.string.invalid_empty_deeplink))
                                }
                            },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (deeprInfo.link.isNotEmpty()) {
                                        ClearInputIconButton(
                                            onClick = {
                                                deeprInfo = deeprInfo.copy(link = "")
                                                isError = false
                                            },
                                        )
                                    }
                                    if (qrScannerLauncher != null) {
                                        IconButton(onClick = { qrScannerLauncher() }) {
                                            Icon(
                                                imageVector = TablerIcons.Qrcode,
                                                contentDescription = stringResource(Res.string.qr_scanner),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                        )

                        FilledTonalButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = deeprInfo.link.isNotBlank() && !isFetchingMetadata,
                            onClick = fetchMetadata,
                        ) {
                            if (isFetchingMetadata) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Icon(
                                    imageVector = TablerIcons.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(stringResource(Res.string.fetch_name_from_link))
                        }
                    }
                }

                // Thumbnail Preview Section
                AnimatedVisibility(
                    visible = deeprInfo.thumbnail.isNotEmpty() && isThumbnailEnable,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = TablerIcons.Photo,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "Preview",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }

                            AsyncImage(
                                model = deeprInfo.thumbnail,
                                contentDescription = deeprInfo.name,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1.91f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                placeholder = null,
                                error = null,
                                contentScale = ContentScale.Crop,
                            )

                            // Thumbnail URL field
                            OutlinedTextField(
                                value = deeprInfo.thumbnail,
                                onValueChange = {
                                    deeprInfo = deeprInfo.copy(thumbnail = it)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(Res.string.thumbnail_url)) },
                                trailingIcon =
                                    if (deeprInfo.thumbnail.isEmpty()) {
                                        null
                                    } else {
                                        {
                                            ClearInputIconButton(
                                                onClick = {
                                                    deeprInfo = deeprInfo.copy(thumbnail = "")
                                                },
                                            )
                                        }
                                    },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                            )

                            TextButton(
                                onClick = { deeprInfo = deeprInfo.copy(thumbnail = "") },
                                modifier = Modifier.align(Alignment.End),
                            ) {
                                Icon(
                                    imageVector = TablerIcons.X,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(Res.string.remove_thumbnail))
                            }
                        }
                    }
                }

                // Details Section
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Name Field
                        OutlinedTextField(
                            value = deeprInfo.name,
                            onValueChange = {
                                deeprInfo = deeprInfo.copy(name = it)
                                isNameError = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(Res.string.enter_link_name)) },
                            supportingText = {
                                if (isNameError) {
                                    Text(text = stringResource(Res.string.enter_link_name_error))
                                }
                            },
                            trailingIcon =
                                if (deeprInfo.name.isEmpty()) {
                                    null
                                } else {
                                    {
                                        ClearInputIconButton(
                                            onClick = {
                                                deeprInfo = deeprInfo.copy(name = "")
                                                isNameError = false
                                            },
                                        )
                                    }
                                },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Notes Field
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = TablerIcons.Note,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 16.dp),
                            )
                            OutlinedTextField(
                                value = deeprInfo.notes,
                                onValueChange = {
                                    deeprInfo = deeprInfo.copy(notes = it)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(Res.string.enter_notes)) },
                                trailingIcon =
                                    if (deeprInfo.notes.isEmpty()) {
                                        null
                                    } else {
                                        {
                                            ClearInputIconButton(
                                                onClick = {
                                                    deeprInfo = deeprInfo.copy(notes = "")
                                                },
                                            )
                                        }
                                    },
                                minLines = 2,
                                maxLines = 4,
                                shape = RoundedCornerShape(12.dp),
                            )
                        }
                    }
                }

                // Profile Selection Section
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = TablerIcons.User,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(Res.string.profile),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        // Profile Dropdown
                        var profileExpanded by remember { mutableStateOf(false) }
                        val selectedProfile = allProfiles.firstOrNull { it.id == selectedProfileId }

                        ExposedDropdownMenuBox(
                            expanded = profileExpanded,
                            onExpandedChange = { profileExpanded = !profileExpanded },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedTextField(
                                value = selectedProfile?.name ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(Res.string.select_profile)) },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = profileExpanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                            )

                            ExposedDropdownMenu(
                                expanded = profileExpanded,
                                onDismissRequest = { profileExpanded = false },
                            ) {
                                allProfiles.forEach { profile ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                if (profile.id == selectedProfileId) {
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
                                            selectedProfileId = profile.id
                                            profileExpanded = false
                                        },
                                    )
                                }

                                if (allProfiles.isNotEmpty()) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                }

                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Icon(
                                                imageVector = TablerIcons.Plus,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                            Text(
                                                text = stringResource(Res.string.create_profile),
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    },
                                    onClick = {
                                        profileExpanded = false
                                        showCreateProfileDialog = true
                                    },
                                )
                            }
                        }
                    }
                }

                // Tags Section
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = TablerIcons.Tag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(Res.string.tags),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        // Tag Input with Autocomplete
                        var expanded by remember { mutableStateOf(false) }
                        val exactMatchExists =
                            allTags.any {
                                it.name.equals(newTagName, ignoreCase = true)
                            }
                        val filteredTags =
                            allTags.filter {
                                it.name.contains(newTagName, ignoreCase = true) &&
                                    !selectedTags.contains(it)
                            }
                        val alreadySelected =
                            selectedTags.any {
                                it.name.equals(newTagName, ignoreCase = true)
                            }
                        val showCreateOption = newTagName.isNotBlank() && !exactMatchExists && !alreadySelected

                        ExposedDropdownMenuBox(
                            expanded = expanded && (showCreateOption || filteredTags.isNotEmpty()),
                            onExpandedChange = { expanded = !expanded },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedTextField(
                                value = newTagName,
                                onValueChange = {
                                    newTagName = it
                                    expanded = true
                                },
                                label = { Text(stringResource(Res.string.add_tag)) },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true),
                                singleLine = true,
                                leadingIcon = {
                                    Icon(
                                        imageVector = TablerIcons.Plus,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                            )

                            ExposedDropdownMenu(
                                expanded = expanded && (showCreateOption || filteredTags.isNotEmpty()),
                                onDismissRequest = { expanded = false },
                            ) {
                                if (showCreateOption) {
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                Icon(
                                                    imageVector = TablerIcons.Plus,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                                Text(
                                                    stringResource(Res.string.create_tag) + ": \"$newTagName\"",
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedTags.add(Tags(0, newTagName))
                                            newTagName = ""
                                            expanded = false
                                        },
                                    )
                                }
                                filteredTags.forEach { tag ->
                                    DropdownMenuItem(
                                        text = { Text(tag.name) },
                                        onClick = {
                                            selectedTags.add(tag)
                                            newTagName = ""
                                            expanded = false
                                        },
                                    )
                                }
                            }
                        }

                        // Selected Tags Display
                        AnimatedVisibility(
                            visible = selectedTags.isNotEmpty(),
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    stringResource(Res.string.tags_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    selectedTags.forEach { tag ->
                                        InputChip(
                                            selected = true,
                                            onClick = { /* Do nothing on click */ },
                                            label = { Text(tag.name) },
                                            trailingIcon = {
                                                IconButton(
                                                    onClick = { selectedTags.remove(tag) },
                                                    modifier = Modifier.size(InputChipDefaults.IconSize),
                                                ) {
                                                    Icon(
                                                        imageVector = TablerIcons.X,
                                                        contentDescription = removeTagText,
                                                    )
                                                }
                                            },
                                            shape = RoundedCornerShape(percent = 50),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (!isCreate) {
                        Button(
                            onClick = {
                                if (isValidDeeplink(deeprInfo.link)) {
                                    save(false)
                                } else {
                                    isError = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                stringResource(Res.string.save),
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                if (isValidDeeplink(deeprInfo.link)) {
                                    save(true)
                                } else {
                                    isError = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                stringResource(Res.string.save_and_execute),
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            FilledTonalButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (isValidDeeplink(deeprInfo.link)) {
                                        save(false)
                                    } else {
                                        isError = true
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(
                                    stringResource(Res.string.save),
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }

                            FilledTonalButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    isError = !openLink(deeprInfo.link)
                                },
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(
                                    stringResource(Res.string.execute),
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Auto-select newly created profile
    LaunchedEffect(allProfiles, pendingProfileNameToSelect) {
        if (pendingProfileNameToSelect != null) {
            val newProfile =
                allProfiles.find {
                    it.name.equals(pendingProfileNameToSelect, ignoreCase = true)
                }
            if (newProfile != null) {
                selectedProfileId = newProfile.id
                pendingProfileNameToSelect = null
            }
        }
    }

    // Create Profile Dialog
    if (showCreateProfileDialog) {
        // State variables are intentionally declared inside the dialog condition block
        // to reset when the dialog is dismissed and reopened, providing a clean state for each use
        var newProfileName by remember { mutableStateOf("") }
        var profileCreationError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = {
                showCreateProfileDialog = false
                profileCreationError = null
            },
            title = {
                Text(stringResource(Res.string.create_profile))
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = {
                            newProfileName = it
                            profileCreationError = null
                        },
                        label = { Text(stringResource(Res.string.profile_name)) },
                        singleLine = true,
                        isError = profileCreationError != null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    profileCreationError?.let { errorMessage ->
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmedProfileName = newProfileName.trim()
                        if (trimmedProfileName.isBlank()) {
                            profileCreationError = profileNameCannotBeBlank
                            return@TextButton
                        }

                        val existingProfile =
                            allProfiles.find {
                                it.name.equals(trimmedProfileName, ignoreCase = true)
                            }

                        if (existingProfile != null) {
                            profileCreationError = profileNameExists
                        } else {
                            viewModel.insertProfile(trimmedProfileName)
                            pendingProfileNameToSelect = trimmedProfileName
                            showCreateProfileDialog = false
                            showToast(profileCreatedSuccessfully)
                        }
                    },
                    enabled = newProfileName.isNotBlank(),
                ) {
                    Text(stringResource(Res.string.create_profile))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateProfileDialog = false
                        profileCreationError = null
                    },
                ) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}
