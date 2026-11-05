package com.yogeshpaliyal.deepr.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.yogeshpaliyal.deepr.ui.resources.Res
import com.yogeshpaliyal.deepr.ui.resources.filter
import com.yogeshpaliyal.deepr.ui.resources.sort_by_date_ascending
import com.yogeshpaliyal.deepr.ui.resources.sort_by_date_descending
import com.yogeshpaliyal.deepr.ui.resources.sort_by_link_ascending
import com.yogeshpaliyal.deepr.ui.resources.sort_by_link_descending
import com.yogeshpaliyal.deepr.ui.resources.sort_by_name_ascending
import com.yogeshpaliyal.deepr.ui.resources.sort_by_name_descending
import com.yogeshpaliyal.deepr.ui.resources.sort_by_opened_ascending
import com.yogeshpaliyal.deepr.ui.resources.sort_by_opened_descending
import com.yogeshpaliyal.deepr.viewmodel.SortType
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowsSort
import compose.icons.tablericons.Calendar
import compose.icons.tablericons.CalendarEvent
import compose.icons.tablericons.Eye
import compose.icons.tablericons.EyeOff
import compose.icons.tablericons.SortAscending
import compose.icons.tablericons.SortDescending
import org.jetbrains.compose.resources.stringResource

@Composable
fun FilterMenu(
    onSortOrderChange: (sortType: @SortType String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(TablerIcons.ArrowsSort, contentDescription = stringResource(Res.string.filter))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.sort_by_date_ascending)) },
                onClick = {
                    onSortOrderChange(SortType.SORT_CREATED_BY_ASC)
                    expanded = false
                },
                leadingIcon = {
                    Icon(
                        TablerIcons.Calendar,
                        contentDescription = null,
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.sort_by_date_descending)) },
                onClick = {
                    onSortOrderChange(SortType.SORT_CREATED_BY_DESC)
                    expanded = false
                },
                leadingIcon = {
                    Icon(
                        TablerIcons.CalendarEvent,
                        contentDescription = null,
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.sort_by_opened_ascending)) },
                onClick = {
                    onSortOrderChange(SortType.SORT_OPENED_ASC)
                    expanded = false
                },
                leadingIcon = {
                    Icon(
                        TablerIcons.Eye,
                        contentDescription = null,
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.sort_by_opened_descending)) },
                onClick = {
                    onSortOrderChange(SortType.SORT_OPENED_DESC)
                    expanded = false
                },
                leadingIcon = {
                    Icon(
                        TablerIcons.EyeOff,
                        contentDescription = null,
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.sort_by_name_ascending)) },
                onClick = {
                    onSortOrderChange(SortType.SORT_NAME_ASC)
                    expanded = false
                },
                leadingIcon = {
                    Icon(
                        TablerIcons.SortAscending,
                        contentDescription = null,
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.sort_by_name_descending)) },
                onClick = {
                    onSortOrderChange(SortType.SORT_NAME_DESC)
                    expanded = false
                },
                leadingIcon = {
                    Icon(
                        TablerIcons.SortDescending,
                        contentDescription = null,
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.sort_by_link_ascending)) },
                onClick = {
                    onSortOrderChange(SortType.SORT_LINK_ASC)
                    expanded = false
                },
                leadingIcon = {
                    Icon(
                        TablerIcons.SortAscending,
                        contentDescription = null,
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.sort_by_link_descending)) },
                onClick = {
                    onSortOrderChange(SortType.SORT_LINK_DESC)
                    expanded = false
                },
                leadingIcon = {
                    Icon(
                        TablerIcons.SortDescending,
                        contentDescription = null,
                    )
                },
            )
        }
    }
}
