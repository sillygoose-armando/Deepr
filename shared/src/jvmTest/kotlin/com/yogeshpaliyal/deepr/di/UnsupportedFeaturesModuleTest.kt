package com.yogeshpaliyal.deepr.di

import com.yogeshpaliyal.deepr.backup.ExportRepository
import com.yogeshpaliyal.deepr.util.RequestResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.java.KoinJavaComponent.getKoin

class UnsupportedFeaturesModuleTest {
    @Test
    fun unsupportedFeaturesReturnNotSupportedError() = runBlocking {
        startKoin {
            modules(unsupportedFeaturesModule("x"))
        }

        try {
            val repo = getKoin().get<ExportRepository>()
            assertEquals(RequestResult.Error("Not supported on x yet"), repo.exportToCsv(null))
        } finally {
            stopKoin()
        }
    }
}
