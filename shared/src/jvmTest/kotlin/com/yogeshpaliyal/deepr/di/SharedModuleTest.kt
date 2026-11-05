package com.yogeshpaliyal.deepr.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.yogeshpaliyal.deepr.DeeprDB
import com.yogeshpaliyal.deepr.DeeprQueries
import kotlin.test.Test
import kotlin.test.assertNotNull
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.java.KoinJavaComponent.getKoin
import java.util.Properties

class SharedModuleTest {
    @Test
    fun sharedModuleProvidesDeeprQueries() {
        val testModule = module {
            single<SqlDriver> {
                val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
                DeeprDB.Schema.create(driver)
                driver
            }
        }

        startKoin {
            modules(sharedModule, testModule)
        }

        try {
            val queries = getKoin().get<DeeprQueries>()
            assertNotNull(queries)
        } finally {
            stopKoin()
        }
    }
}