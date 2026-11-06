package com.yogeshpaliyal.deepr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner

/**
 * Provides a [NavigationEventDispatcherOwner] for hosts that don't (iOS `ComposeUIViewController`, desktop `Window`).
 * Without it, back handlers crash with "CompositionLocal LocalHostDefaultProvider not present" (seen on the iOS simulator).
 * shortcut: a plain dispatcher, so no system back gesture on iOS/desktop; wire the host's back input when a platform needs it.
 */
@Composable
fun ProvideNavigationEventOwner(content: @Composable () -> Unit) {
    val owner =
        remember {
            val dispatcher = NavigationEventDispatcher()
            object : NavigationEventDispatcherOwner {
                override val navigationEventDispatcher: NavigationEventDispatcher = dispatcher
            }
        }
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner, content = content)
}
