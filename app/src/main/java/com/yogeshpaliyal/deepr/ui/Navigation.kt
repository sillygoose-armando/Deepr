package com.yogeshpaliyal.deepr.ui

import androidx.compose.runtime.Composable
import com.yogeshpaliyal.deepr.GetLinksAndTags
import com.yogeshpaliyal.deepr.ui.screens.addlink.AddLinkScreen

data class AddLinkScreen(
    val selectedLink: GetLinksAndTags,
) : Screen {
    @Composable
    override fun Content() {
        AddLinkScreen(selectedLink = selectedLink)
    }
}
