package com.yogeshpaliyal.deepr.ui.platform

import androidx.compose.runtime.Composable
import com.yogeshpaliyal.deepr.viewmodel.AccountViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal actual fun koinSharedAccountViewModel(): AccountViewModel {
    // TODO(#11): iOS — use koinViewModel(viewModelStoreOwner = <a root owner>)
    return koinViewModel<AccountViewModel>()
}
