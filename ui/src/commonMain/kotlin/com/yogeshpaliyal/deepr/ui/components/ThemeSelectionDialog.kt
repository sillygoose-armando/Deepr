package com.yogeshpaliyal.deepr.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yogeshpaliyal.deepr.ui.resources.Res
import com.yogeshpaliyal.deepr.ui.resources.close
import com.yogeshpaliyal.deepr.ui.resources.system_default
import com.yogeshpaliyal.deepr.ui.resources.theme_dark
import com.yogeshpaliyal.deepr.ui.resources.theme_dialog_title
import com.yogeshpaliyal.deepr.ui.resources.theme_light
import org.jetbrains.compose.resources.stringResource

@Composable
fun ThemeSelectionDialog(
    currentThemeMode: String,
    onThemeSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val themeOptions =
        listOf(
            "system" to stringResource(Res.string.system_default),
            "light" to stringResource(Res.string.theme_light),
            "dark" to stringResource(Res.string.theme_dark),
        )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.theme_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column {
                themeOptions.forEach { (mode, label) ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onThemeSelect(mode) }
                                .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = currentThemeMode == mode,
                            onClick = { onThemeSelect(mode) },
                        )
                        Text(
                            text = label,
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.close))
            }
        },
    )
}
