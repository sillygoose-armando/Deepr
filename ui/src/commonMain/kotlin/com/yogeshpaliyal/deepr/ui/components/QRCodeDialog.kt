package com.yogeshpaliyal.deepr.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.qrose.options.QrBrush
import io.github.alexzhirkevich.qrose.options.QrPixelShape
import io.github.alexzhirkevich.qrose.options.circle
import io.github.alexzhirkevich.qrose.options.solid
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import com.yogeshpaliyal.deepr.GetLinksAndTags
import com.yogeshpaliyal.deepr.ui.resources.Res
import com.yogeshpaliyal.deepr.ui.resources.app_logo
import com.yogeshpaliyal.deepr.ui.resources.show_qr_code
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun QrCodeDialog(
    deepr: GetLinksAndTags,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.show_qr_code)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier =
                        Modifier
                            .background(
                                color = Color.White,
                                shape = RoundedCornerShape(12.dp),
                            ).padding(16.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(268.dp).background(Color.White),
                    ) {
                        Image(
                            painter =
                                rememberQrCodePainter(data = deepr.link) {
                                    shapes { darkPixel = QrPixelShape.circle() }
                                    colors { dark = QrBrush.solid(Color.Black) }
                                },
                            contentDescription = stringResource(Res.string.show_qr_code),
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Transparent),
                        ) {
                            Image(
                                painter = painterResource(Res.drawable.app_logo),
                                contentDescription = stringResource(Res.string.app_logo),
                                modifier =
                                    Modifier
                                        .fillMaxSize(0.5f)
                                        .clip(CircleShape)
                                        .background(Color.White),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}
