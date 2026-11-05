package com.yogeshpaliyal.deepr.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.yogeshpaliyal.deepr.GetLinksAndTags
import com.yogeshpaliyal.deepr.Profile
import org.jetbrains.compose.resources.StringResource
import kotlin.collections.remove

val LocalNavigator =
    compositionLocalOf<TopLevelBackStack<BaseScreen>> { error("LocalNavigator not provided") }

// Bridge for ProfileTheme (stays in :app pro/freeGithub source sets until M2-13).
// ProfileManagementScreen calls this in its edit dialog for pro users.
// Default: null skips the theme customization section on non-pro builds.
val LocalProfileThemeProvider =
    compositionLocalOf<(@Composable (profile: Profile, onProfileUpdate: (Profile) -> Unit) -> Unit)?> { null }

// Bridge for the server status bar rendered in HomeScreen's Scaffold topBar.
// Provided by :app's MainActivity; defaults to null (no bar shown).
val LocalHomeServerBarBuilder =
    compositionLocalOf<(@Composable (onServerStatusClick: () -> Unit, modifier: Modifier) -> Unit)?> { null }

// Bridge for the note-view dialog used from HomeScreen's Content.
// Provided by :app's MainActivity; defaults to null (note viewing unavailable).
val LocalHomeNoteViewDialogBuilder =
    compositionLocalOf<(@Composable (deepr: GetLinksAndTags, onDismiss: () -> Unit, onEdit: (GetLinksAndTags) -> Unit, modifier: Modifier) -> Unit)?> { null }

sealed interface BaseScreen : NavKey

interface Screen : BaseScreen {
    @Composable
    fun Content()
}

interface TopLevelRoute : BaseScreen {
    val icon: ImageVector

    val label: StringResource

    @Composable
    fun Content(windowInsets: WindowInsets)
}

class TopLevelBackStack<T : Any>(
    startKey: T,
) {
    // Maintain a stack for each top level route
    private var topLevelStacks: LinkedHashMap<T, SnapshotStateList<T>> =
        linkedMapOf(
            startKey to mutableStateListOf(startKey),
        )

    // Expose the current top level route for consumers
    var topLevelKey by mutableStateOf(startKey)
        private set

    // Expose the back stack so it can be rendered by the NavDisplay
    val backStack = mutableStateListOf(startKey)

    private fun updateBackStack() =
        backStack.apply {
            clear()
            addAll(topLevelStacks.flatMap { it.value })
        }

    fun clearStackAndAdd(key: T) {
        topLevelStacks.clear()
        addTopLevel(key)
    }

    fun addTopLevel(key: T) {
        // If the top level doesn't exist, add it
        if (topLevelStacks[key] == null) {
            topLevelStacks.put(key, mutableStateListOf(key))
        } else {
            // Otherwise just move it to the end of the stacks
            topLevelStacks.apply {
                remove(key)?.let {
                    put(key, it)
                }
            }
        }
        topLevelKey = key
        updateBackStack()
    }

    fun add(key: T) {
        topLevelStacks[topLevelKey]?.add(key)
        updateBackStack()
    }

    fun getLast() = backStack.last()

    fun removeLast() {
        val removedKey = topLevelStacks[topLevelKey]?.removeLastOrNull()
        // If the removed key was a top level key, remove the associated top level stack
        topLevelStacks.remove(removedKey)
        topLevelKey = topLevelStacks.keys.last()
        updateBackStack()
    }
}
