package com.yogeshpaliyal.deepr.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yogeshpaliyal.deepr.GetLinksAndTags
import com.yogeshpaliyal.deepr.ui.resources.Res
import com.yogeshpaliyal.deepr.ui.resources.show_qr_code
import compose.icons.TablerIcons
import compose.icons.tablericons.Qrcode
import org.jetbrains.compose.resources.stringResource

@Composable
fun ShowQRCodeMenuItem(
    account: GetLinksAndTags,
    onQrCodeClick: (GetLinksAndTags) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.ListItem(
        modifier =
            modifier.clickable {
                onQrCodeClick(account)
            },
        headlineContent = { Text(stringResource(Res.string.show_qr_code)) },
        leadingContent = {
            Icon(
                TablerIcons.Qrcode,
                contentDescription = stringResource(Res.string.show_qr_code),
            )
        },
    )
}