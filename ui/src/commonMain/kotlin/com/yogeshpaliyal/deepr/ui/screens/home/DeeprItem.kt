package com.yogeshpaliyal.deepr.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.yogeshpaliyal.deepr.GetLinksAndTags
import com.yogeshpaliyal.deepr.Tags
import com.yogeshpaliyal.deepr.ui.getDeeprItemBackgroundColor
import com.yogeshpaliyal.deepr.ui.getDeeprItemTextColor
import com.yogeshpaliyal.deepr.ui.resources.Res
import com.yogeshpaliyal.deepr.ui.resources.add_to_favourites
import com.yogeshpaliyal.deepr.ui.resources.link_copied
import com.yogeshpaliyal.deepr.ui.resources.load_more_tags
import com.yogeshpaliyal.deepr.ui.resources.more_options
import com.yogeshpaliyal.deepr.ui.resources.opened_count
import com.yogeshpaliyal.deepr.ui.resources.remove_from_favourites
import com.yogeshpaliyal.deepr.ui.resources.show_less_tags
import compose.icons.TablerIcons
import compose.icons.tablericons.DotsVertical
import org.jetbrains.compose.resources.stringResource

/**
 * Platform-specific copy-link action.
 * Android: system clipboard via ClipboardManager + Toast.
 * JVM/iOS: LocalClipboardManager.clipboard.setText(AnnotatedString(text)), no toast.
 */
@Composable
internal expect fun rememberCopyLinkAction(): (label: String, text: String) -> Unit

/**
 * Formats a date-time string from "yyyy-MM-dd HH:mm:ss" (UTC) into a
 * locale-friendly display format.
 * On failure returns the input unchanged.
 */
expect fun formatDateTime(dateTimeString: String): String

sealed class MenuItem(
    val item: GetLinksAndTags,
) {
    class Click(
        item: GetLinksAndTags,
    ) : MenuItem(item)

    class Copy(
        item: GetLinksAndTags,
    ) : MenuItem(item)

    class Shortcut(
        item: GetLinksAndTags,
    ) : MenuItem(item)

    class ShowQrCode(
        item: GetLinksAndTags,
    ) : MenuItem(item)

    class FavouriteClick(
        item: GetLinksAndTags,
    ) : MenuItem(item)

    class OpenWith(
        item: GetLinksAndTags,
    ) : MenuItem(item)

    class Share(
        item: GetLinksAndTags,
    ) : MenuItem(item)

    class Edit(
        item: GetLinksAndTags,
    ) : MenuItem(item)

    class ResetCounter(
        item: GetLinksAndTags,
    ) : MenuItem(item)

    class Delete(
        item: GetLinksAndTags,
    ) : MenuItem(item)

    class MoreOptionsBottomSheet(
        item: GetLinksAndTags,
    ) : MenuItem(item)

    class ViewNote(
        item: GetLinksAndTags,
    ) : MenuItem(item)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeeprItem(
    account: GetLinksAndTags,
    onItemClick: (MenuItem) -> Unit,
    onTagClick: (tag: String) -> Unit,
    selectedTag: List<Tags>,
    isThumbnailEnable: Boolean,
    modifier: Modifier = Modifier,
    showOpenCounter: Boolean = true,
    analyticsManager: com.yogeshpaliyal.deepr.analytics.AnalyticsManager = org.koin.compose.koinInject(),
) {
    var tagsExpanded by remember { mutableStateOf(false) }

    val linkCopied = stringResource(Res.string.link_copied)
    val copyLinkAction = rememberCopyLinkAction()

    val selectedTags =
        remember(account.tagsNames) { account.tagsNames?.split(",")?.toMutableList() }

    DeeprItemSwipable(account, onItemClick, modifier) {
        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor = getDeeprItemBackgroundColor(account.isFavourite),
                ),
            modifier =
                Modifier
                    .testTag("DeeprItem")
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onItemClick(MenuItem.Click(account)) },
                        onLongClick = {
                            analyticsManager.logEvent(
                                com.yogeshpaliyal.deepr.analytics.AnalyticsEvents.COPY_LINK,
                                mapOf(com.yogeshpaliyal.deepr.analytics.AnalyticsParams.LINK_ID to account.id),
                            )
                            copyLinkAction(linkCopied, account.link)
                        },
                    ),
        ) {
            if (account.thumbnail.isNotEmpty() && isThumbnailEnable) {
                AsyncImage(
                    model = account.thumbnail,
                    contentDescription = account.name,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.91f)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    placeholder = null,
                    error = null,
                    contentScale = ContentScale.Crop,
                )
            }
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (account.name.isNotEmpty()) {
                            Text(
                                text = account.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge,
                                color = getDeeprItemTextColor(account.isFavourite),
                            )
                        }
                        Text(
                            text = account.link,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = getDeeprItemTextColor(account.isFavourite),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = formatDateTime(account.createdAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = getDeeprItemTextColor(account.isFavourite),
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Row {
                            IconButton(onClick = {
                                onItemClick(MenuItem.FavouriteClick(account))
                            }) {
                                Icon(
                                    imageVector =
                                        if (account.isFavourite == 1L) {
                                            Icons.Rounded.Star
                                        } else {
                                            Icons.Rounded.StarBorder
                                        },
                                    contentDescription =
                                        if (account.isFavourite == 1L) {
                                            stringResource(Res.string.remove_from_favourites)
                                        } else {
                                            stringResource(Res.string.add_to_favourites)
                                        },
                                    tint = getDeeprItemTextColor(account.isFavourite),
                                    modifier = Modifier.size(28.dp),
                                )
                            }

                            IconButton(onClick = {
                                onItemClick(
                                    MenuItem.MoreOptionsBottomSheet(
                                        account,
                                    ),
                                )
                            }) {
                                Icon(
                                    TablerIcons.DotsVertical,
                                    contentDescription = stringResource(Res.string.more_options),
                                )
                            }
                        }

                        if (showOpenCounter) {
                            Text(
                                text = stringResource(Res.string.opened_count, account.openedCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = getDeeprItemTextColor(account.isFavourite),
                            )
                        }
                    }
                }

                Column {
                    // Determine max tags to show based on expanded state
                    val maxTagsToShow = if (tagsExpanded) selectedTags?.size ?: 0 else 9
                    val visibleTags = selectedTags?.take(maxTagsToShow) ?: emptyList()
                    val hiddenTagsCount = (selectedTags?.size ?: 0) - visibleTags.size

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        visibleTags.forEach { tag ->
                            val isSelected = selectedTag.any { it.name == tag.trim() }
                            FilterChip(
                                modifier = Modifier.padding(0.dp),
                                elevation = null,
                                selected = isSelected,
                                onClick = { onTagClick(tag.trim()) },
                                label = { Text(tag.trim()) },
                                shape = RoundedCornerShape(percent = 50),
                            )
                        }
                    }

                    // Show "Load More" or "Show Less" button if there are more than 9 tags
                    if ((selectedTags?.size ?: 0) > 9) {
                        androidx.compose.material3.TextButton(
                            onClick = { tagsExpanded = !tagsExpanded },
                            modifier = Modifier.padding(start = 4.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Text(
                                text =
                                    if (tagsExpanded) {
                                        stringResource(Res.string.show_less_tags)
                                    } else {
                                        stringResource(Res.string.load_more_tags, hiddenTagsCount)
                                    },
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Formats a link's information into a shareable text format.
 *
 * @param item The link item containing name, link, and notes
 * @return A formatted string containing the link's name (if not blank),
 *         the link URL, and notes (if not blank), separated by double newlines
 */
fun formatShareText(item: GetLinksAndTags): String =
    buildString {
        if (item.name.isNotBlank()) {
            append(item.name)
            append("\n")
        }
        append(item.link)
        if (item.notes.isNotBlank()) {
            append("\n\n")
            append(item.notes)
        }
    }
