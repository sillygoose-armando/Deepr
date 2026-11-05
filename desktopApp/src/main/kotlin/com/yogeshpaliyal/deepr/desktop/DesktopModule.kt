package com.yogeshpaliyal.deepr.desktop

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.yogeshpaliyal.deepr.DeeprDB
import com.yogeshpaliyal.deepr.data.LinkRepository
import com.yogeshpaliyal.deepr.data.LinkRepositoryImpl
import com.yogeshpaliyal.deepr.preference.AppPreferenceDataStore
import com.yogeshpaliyal.deepr.preference.PreferenceRepository
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
}
