package com.yogeshpaliyal.deepr

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.cash.sqldelight.logs.LogSqliteDriver
import com.yogeshpaliyal.deepr.analytics.AnalyticsManager
import com.yogeshpaliyal.deepr.analytics.AnalyticsManagerFactoryImpl
import com.yogeshpaliyal.deepr.backup.AutoBackupRunner
import com.yogeshpaliyal.deepr.backup.AutoBackupWorker
import com.yogeshpaliyal.deepr.backup.ExportRepository
import com.yogeshpaliyal.deepr.backup.ExportRepositoryImpl
import com.yogeshpaliyal.deepr.backup.GoogleDriveAutoBackupWorker
import com.yogeshpaliyal.deepr.backup.ImportRepository
import com.yogeshpaliyal.deepr.backup.ImportRepositoryImpl
import com.yogeshpaliyal.deepr.data.LinkRepository
import com.yogeshpaliyal.deepr.data.LinkRepositoryImpl
import com.yogeshpaliyal.deepr.di.p2pModule
import com.yogeshpaliyal.deepr.di.sharedModule
import com.yogeshpaliyal.deepr.gdrive.DriveSyncManager
import com.yogeshpaliyal.deepr.gdrive.DriveSyncManagerFactoryImpl
import com.yogeshpaliyal.deepr.p2p.P2pSyncController
import com.yogeshpaliyal.deepr.p2p.jvm.JvmP2pCrypto
import com.yogeshpaliyal.deepr.p2p.node.KeystoreSecretStore
import com.yogeshpaliyal.deepr.preference.AppPreferenceDataStore
import com.yogeshpaliyal.deepr.preference.PreferenceRepository
import com.yogeshpaliyal.deepr.preference.appDataStore
import com.yogeshpaliyal.deepr.review.ReviewManager
import com.yogeshpaliyal.deepr.review.ReviewManagerFactory
import com.yogeshpaliyal.deepr.security.AppLockState
import com.yogeshpaliyal.deepr.server.LocalServerRepository
import com.yogeshpaliyal.deepr.server.LocalServerRepositoryImpl
import com.yogeshpaliyal.deepr.server.LocalServerTransferLink
import com.yogeshpaliyal.deepr.sync.P2pSyncWorker
import com.yogeshpaliyal.deepr.sync.SyncRepository
import com.yogeshpaliyal.deepr.sync.SyncRepositoryImpl
import com.yogeshpaliyal.deepr.viewmodel.LocalServerViewModel
import com.yogeshpaliyal.deepr.viewmodel.TransferLinkLocalServerViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.binds
import org.koin.dsl.module
import org.koin.mp.KoinPlatform

class DeeprApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Re-lock the app whenever it leaves the foreground, so app-lock (when enabled)
        // requires re-authentication each time the app is brought back.
        // Also start/stop P2P sync when the app enters/leaves the foreground.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    KoinPlatform.getKoin().get<P2pSyncController>().onForeground()
                }

                override fun onStop(owner: LifecycleOwner) {
                    AppLockState.isUnlocked.value = false
                    KoinPlatform.getKoin().get<P2pSyncController>().onBackground()
                }
            },
        )

        val appModule =
            module {
                // Provide the Android-specific SqlDriver
                single<SqlDriver> {
                    LogSqliteDriver(
                        AndroidSqliteDriver(
                            schema = DeeprDB.Schema,
                            context = this@DeeprApplication,
                            name = "deepr.db",
                            callback =
                                object : AndroidSqliteDriver.Callback(DeeprDB.Schema) {
                                    override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                                        super.onOpen(db)
                                        db.setForeignKeyConstraintsEnabled(true)
                                    }
                                },
                        ),
                    ) {
                        Log.d("loggingDB", it)
                    }
                }

                single<PreferenceRepository> { AppPreferenceDataStore(androidContext().appDataStore) }

                single<LinkRepository> {
                    val context = androidContext()
                    LinkRepositoryImpl(get()) { GoogleDriveAutoBackupWorker.scheduleBackup(context) }
                }

                single<ExportRepository> { ExportRepositoryImpl(androidContext(), get()) }
                single<DriveSyncManager> { DriveSyncManagerFactoryImpl.create(androidContext(), get()) }

                single<ImportRepository> { ImportRepositoryImpl(androidContext(), get(), get()) }

                single<SyncRepository> { SyncRepositoryImpl(androidContext(), get(), get()) }

                single { AutoBackupWorker(androidContext(), get(), get()) } binds
                    arrayOf(
                        AutoBackupWorker::class,
                        AutoBackupRunner::class,
                    )

                single {
                    HttpClient(CIO) {
                        install(ContentNegotiation) {
                            json(
                                Json {
                                    prettyPrint = true
                                    isLenient = true
                                    ignoreUnknownKeys = true
                                },
                            )
                        }
                    }
                }

                single<LocalServerRepository> {
                    LocalServerRepositoryImpl(
                        androidContext(),
                        get(),
                        get(),
                        get(),
                        get(),
                        get(),
                        get(),
                    )
                }

                factory {
                    LocalServerTransferLink(
                        androidContext(),
                        get(),
                        get(),
                        get(),
                        get(),
                        get(),
                        get(),
                    )
                }

                viewModel {
                    LocalServerViewModel(get())
                }

                single<ReviewManager> {
                    ReviewManagerFactory.create()
                }

                viewModel {
                    TransferLinkLocalServerViewModel(get())
                }

                viewModel {
                    TransferLinkLocalServerViewModel(get())
                }
                single<AnalyticsManager> {
                    AnalyticsManagerFactoryImpl.create(get())
                }
            }

        startKoin {
            // Provide Android context to Koin
            androidContext(this@DeeprApplication)

            modules(sharedModule, appModule, p2pModule(JvmP2pCrypto(), KeystoreSecretStore()))
        }

        // Schedule periodic P2P sync in the background
        P2pSyncWorker.schedule(this)
    }
}
