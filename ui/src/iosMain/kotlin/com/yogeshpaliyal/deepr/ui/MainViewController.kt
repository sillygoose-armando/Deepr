package com.yogeshpaliyal.deepr.ui

import androidx.compose.ui.window.ComposeUIViewController
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.yogeshpaliyal.deepr.DeeprDB
import com.yogeshpaliyal.deepr.data.LinkRepository
import com.yogeshpaliyal.deepr.data.LinkRepositoryImpl
import com.yogeshpaliyal.deepr.di.p2pModule
import com.yogeshpaliyal.deepr.di.sharedModule
import com.yogeshpaliyal.deepr.di.unsupportedFeaturesModule
import com.yogeshpaliyal.deepr.p2p.P2pSyncController
import com.yogeshpaliyal.deepr.p2p.ios.CryptoKitP2pCrypto
import com.yogeshpaliyal.deepr.p2p.node.KeychainSecretStore
import com.yogeshpaliyal.deepr.preference.AppPreferenceDataStore
import com.yogeshpaliyal.deepr.preference.PreferenceRepository
import com.yogeshpaliyal.deepr.ui.theme.DeeprThemeFromPreferences
import okio.Path.Companion.toPath
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIViewController
import platform.Foundation.NSNotificationCenter

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
fun initKoin(dataDir: String, withP2p: Boolean = true) {
    val modulesList = mutableListOf(sharedModule, iosModule(dataDir), unsupportedFeaturesModule("iOS"))
    if (withP2p) {
        modulesList.add(
            p2pModule(
                CryptoKitP2pCrypto(),
                KeychainSecretStore(CryptoKitP2pCrypto()),
            ),
        )
    }
    startKoin {
        modules(*modulesList.toTypedArray())
    }

    if (withP2p) {
        // Observe iOS app lifecycle to start/stop P2P sync
        NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = null,
        ) { KoinPlatform.getKoin().get<P2pSyncController>().onForeground() }
        NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = null,
        ) { KoinPlatform.getKoin().get<P2pSyncController>().onBackground() }
    }
}

/** The Compose root view controller for the Xcode app, mirroring desktop `Main.kt`. */
fun MainViewController(): UIViewController =
    ComposeUIViewController {
        ProvideNavigationEventOwner {
            DeeprThemeFromPreferences {
                val (clipboardLink, resetClipboardLink) = rememberDetectedClipboardLink()
                DeeprApp(
                    sharedText = null,
                    resetSharedText = {},
                    clipboardLink = clipboardLink,
                    resetClipboardLink = resetClipboardLink,
                )
            }
        }
    }
