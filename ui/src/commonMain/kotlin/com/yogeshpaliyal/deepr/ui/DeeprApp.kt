package com.yogeshpaliyal.deepr.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.yogeshpaliyal.deepr.ClipboardLink
import com.yogeshpaliyal.deepr.LocalClipboardLink
import com.yogeshpaliyal.deepr.LocalSharedText
import com.yogeshpaliyal.deepr.SharedLink
import com.yogeshpaliyal.deepr.ui.screens.Settings
import com.yogeshpaliyal.deepr.ui.screens.home.Dashboard2
import com.yogeshpaliyal.deepr.ui.screens.home.TagSelectionScreen
import org.jetbrains.compose.resources.stringResource

private val TOP_LEVEL_ROUTES: List<TopLevelRoute> =
    listOf(Dashboard2(), TagSelectionScreen, Settings)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeeprApp(
    sharedText: SharedLink?,
    resetSharedText: () -> Unit,
    clipboardLink: ClipboardLink?,
    resetClipboardLink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backStack =
        remember {
            TopLevelBackStack<BaseScreen>(
                Dashboard2(),
            )
        }
    val current = backStack.getLast()
    val scrollBehavior = BottomAppBarDefaults.exitAlwaysScrollBehavior()
    val hapticFeedback = LocalHapticFeedback.current
    val layoutDirection = LocalLayoutDirection.current

    CompositionLocalProvider(LocalSharedText provides Pair(sharedText, resetSharedText)) {
        CompositionLocalProvider(LocalClipboardLink provides Pair(clipboardLink, resetClipboardLink)) {
            CompositionLocalProvider(
                LocalNavigator provides backStack,
            ) {
                Scaffold(
                    modifier = modifier,
                    bottomBar = {
                        AnimatedVisibility(
                            (TOP_LEVEL_ROUTES.any { it::class == current::class }),
                            enter = slideInVertically(initialOffsetY = { it }),
                            exit = slideOutVertically(targetOffsetY = { it }),
                        ) {
                            BottomAppBar(scrollBehavior = scrollBehavior) {
                                TOP_LEVEL_ROUTES.forEach { topLevelRoute ->
                                    val isSelected =
                                        topLevelRoute::class == backStack.topLevelKey::class
                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                                            backStack.addTopLevel(topLevelRoute)
                                        },
                                        label = {
                                            Text(stringResource(topLevelRoute.label))
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = topLevelRoute.icon,
                                                contentDescription = null,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    },
                ) { contentPadding ->
                    NavDisplay(
                        backStack = backStack.backStack,
                        entryDecorators =
                            listOf(
                                rememberSaveableStateHolderNavEntryDecorator(),
                                rememberViewModelStoreNavEntryDecorator(),
                            ),
                        onBack = {
                            backStack.removeLast()
                        },
                        entryProvider = {
                            NavEntry(it) { entryItem ->
                                if (entryItem is TopLevelRoute) {
                                    entryItem.Content(
                                        WindowInsets(
                                            left = contentPadding.calculateLeftPadding(layoutDirection),
                                            right = contentPadding.calculateRightPadding(layoutDirection),
                                            top = contentPadding.calculateTopPadding(),
                                            bottom = contentPadding.calculateBottomPadding(),
                                        ),
                                    )
                                } else if (entryItem is Screen) {
                                    entryItem.Content()
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
