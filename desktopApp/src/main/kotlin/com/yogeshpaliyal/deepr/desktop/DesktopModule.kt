package com.yogeshpaliyal.deepr.desktop

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.yogeshpaliyal.deepr.DeeprDB
import com.yogeshpaliyal.deepr.analytics.AnalyticsManager
import com.yogeshpaliyal.deepr.backup.AutoBackupRunner
import com.yogeshpaliyal.deepr.backup.ExportRepository
import com.yogeshpaliyal.deepr.backup.ImportRepository
import com.yogeshpaliyal.deepr.backup.ImportResult
import com.yogeshpaliyal.deepr.backup.importer.BookmarkImporter
import com.yogeshpaliyal.deepr.data.LinkRepository
import com.yogeshpaliyal.deepr.data.LinkRepositoryImpl
import com.yogeshpaliyal.deepr.preference.AppPreferenceDataStore
import com.yogeshpaliyal.deepr.preference.PreferenceRepository
import com.yogeshpaliyal.deepr.sync.SyncRepository
import com.yogeshpaliyal.deepr.util.RequestResult
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import okio.Path.Companion.toPath
import org.koin.dsl.module
import java.io.File
import java.util.Properties

fun desktopModule(dataDir: File) = module {
    single<SqlDriver> {
        JdbcSqliteDriver(
            "jdbc:sqlite:${File(dataDir, "deepr.db").absolutePath}",
            Properties().apply { put("foreign_keys", "true") },
            DeeprDB.Schema,
        )
    }

    single<PreferenceRepository> {
        AppPreferenceDataStore(
            PreferenceDataStoreFactory.createWithPath(
                produceFile = { File(dataDir, "app_data_store.preferences_pb").absolutePath.toPath() },
            ),
        )
    }

    single<LinkRepository> {
        LinkRepositoryImpl(get())
    }

    single<ExportRepository> {
        object : ExportRepository {
            override suspend fun exportToCsv(uri: String?): RequestResult<String> =
                RequestResult.Error("Not supported on desktop yet")
        }
    }

    single<ImportRepository> {
        object : ImportRepository {
            override suspend fun importFromCsv(uri: String): RequestResult<ImportResult> =
                RequestResult.Error("Not supported on desktop yet")

            override fun getAvailableImporters(): List<BookmarkImporter> = emptyList()

            override suspend fun importBookmarks(
                uri: String,
                importer: BookmarkImporter,
            ): RequestResult<ImportResult> =
                RequestResult.Error("Not supported on desktop yet")
        }
    }

    single<SyncRepository> {
        object : SyncRepository {
            override suspend fun syncToMarkdown(): RequestResult<String> =
                RequestResult.Error("Not supported on desktop yet")

            override suspend fun validateMarkdownFile(filePath: String): RequestResult<Boolean> =
                RequestResult.Error("Not supported on desktop yet")

            override suspend fun recordSyncTime() {}
        }
    }

    single<AutoBackupRunner> {
        object : AutoBackupRunner {
            override suspend fun doWork() {}
        }
    }

    single<AnalyticsManager> {
        object : AnalyticsManager {
            override fun logEvent(
                eventName: String,
                params: Map<String, Any>,
            ) {}

            override fun setUserProperty(
                propertyName: String,
                value: String,
            ) {}
        }
    }
}
