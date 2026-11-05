package com.yogeshpaliyal.deepr.ui.platform

import androidx.compose.runtime.Composable
import com.yogeshpaliyal.deepr.viewmodel.AccountViewModel
import org.koin.compose.viewmodel.koinActivityViewModel

@Composable
internal actual fun koinSharedAccountViewModel(): AccountViewModel =
    koinActivityViewModel()
