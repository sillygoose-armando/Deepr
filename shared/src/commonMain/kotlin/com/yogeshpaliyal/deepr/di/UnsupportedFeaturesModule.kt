package com.yogeshpaliyal.deepr.di

import com.yogeshpaliyal.deepr.analytics.AnalyticsManager
import com.yogeshpaliyal.deepr.backup.AutoBackupRunner
import com.yogeshpaliyal.deepr.backup.ExportRepository
import com.yogeshpaliyal.deepr.backup.ImportRepository
import com.yogeshpaliyal.deepr.backup.ImportResult
import com.yogeshpaliyal.deepr.backup.importer.BookmarkImporter
import com.yogeshpaliyal.deepr.sync.SyncRepository
import com.yogeshpaliyal.deepr.util.RequestResult
import org.koin.dsl.module

/**
 * Stub implementations for features that are not available on a given platform
 * (desktop, iOS). Each returns an error/does nothing, so the shared graph
 * resolves uniformly everywhere. Migrated off `desktopApp` (M4-01).
 */
fun unsupportedFeaturesModule(platform: String) = module {
    single<ExportRepository> {
        object : ExportRepository {
            override suspend fun exportToCsv(uri: String?): RequestResult<String> =
                RequestResult.Error("Not supported on $platform yet")
        }
    }

    single<ImportRepository> {
        object : ImportRepository {
            override suspend fun importFromCsv(uri: String): RequestResult<ImportResult> =
                RequestResult.Error("Not supported on $platform yet")

            override fun getAvailableImporters(): List<BookmarkImporter> = emptyList()

            override suspend fun importBookmarks(
                uri: String,
                importer: BookmarkImporter,
            ): RequestResult<ImportResult> =
                RequestResult.Error("Not supported on $platform yet")
        }
    }

    single<SyncRepository> {
        object : SyncRepository {
            override suspend fun syncToMarkdown(): RequestResult<String> =
                RequestResult.Error("Not supported on $platform yet")

            override suspend fun validateMarkdownFile(filePath: String): RequestResult<Boolean> =
                RequestResult.Error("Not supported on $platform yet")

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
