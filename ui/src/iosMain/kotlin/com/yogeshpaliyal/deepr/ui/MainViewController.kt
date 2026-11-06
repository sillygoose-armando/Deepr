package com.yogeshpaliyal.deepr.ui

import androidx.compose.ui.window.ComposeUIViewController
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.yogeshpaliyal.deepr.DeeprDB
import com.yogeshpaliyal.deepr.data.LinkRepository
import com.yogeshpaliyal.deepr.data.LinkRepositoryImpl
import com.yogeshpaliyal.deepr.di.sharedModule
import com.yogeshpaliyal.deepr.di.unsupportedFeaturesModule
import com.yogeshpaliyal.deepr.preference.AppPreferenceDataStore
import com.yogeshpaliyal.deepr.preference.PreferenceRepository
import com.yogeshpaliyal.deepr.ui.theme.DeeprTheme
import okio.Path.Companion.toPath
import org.koin.core.context.startKoin
import org.koin.dsl.module
import platform.UIKit.UIViewController

fun iosModule(dataDir: String) = module {
    single<SqlDriver> {
        NativeSqliteDriver(
            DeeprDB.Schema,
            "deepr.db",
            onConfiguration = {
                it.copy(
                    extendedConfig = it.extendedConfig.copy(basePath = dataDir, foreignKeyConstraints = true),
                )
            },
        )
    }

    single<PreferenceRepository> {
        AppPreferenceDataStore(
            PreferenceDataStoreFactory.createWithPath(
                produceFile = { "$dataDir/app_data_store.preferences_pb".toPath() },
            ),
        )
    }

    single<LinkRepository> {
        LinkRepositoryImpl(get())
    }
}

/** Start Koin with the shared graph, the iOS platform module and platform stubs. */
fun initKoin(dataDir: String) {
    startKoin {
        modules(sharedModule, iosModule(dataDir), unsupportedFeaturesModule("iOS"))
    }
}

/** The Compose root view controller for the Xcode app, mirroring desktop `Main.kt`. */
fun MainViewController(): UIViewController =
    ComposeUIViewController {
        ProvideNavigationEventOwner {
            DeeprTheme {
                DeeprApp(
                    sharedText = null,
                    resetSharedText = {},
                    clipboardLink = null,
                    resetClipboardLink = {},
                )
            }
        }
    }
