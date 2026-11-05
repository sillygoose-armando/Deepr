package com.yogeshpaliyal.deepr.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.yogeshpaliyal.deepr.DeeprDB
import com.yogeshpaliyal.deepr.preference.AppPreferenceDataStore
import com.yogeshpaliyal.deepr.preference.PreferenceRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SilentSaverTest {
    @Test
    fun invalidLinkReturnsInvalidLink() =
        runBlocking {
            val (saver, _, _) = setup()
            val result = saver.save("not a link", null)
            assertTrue(result is SilentSaveResult.InvalidLink, "Expected InvalidLink but got $result")
        }

    @Test
    fun alreadyExistsAfterInsertingSameLink() =
        runBlocking {
            val (saver, prefRepo, _) = setup()
            prefRepo.setSilentSaveProfileId(1L)

            val firstResult = saver.save("https://example.com", "Example")
            assertTrue(firstResult is SilentSaveResult.Saved, "Expected Saved but got $firstResult")

            val secondResult = saver.save("https://example.com", "Example")
            assertTrue(secondResult is SilentSaveResult.AlreadyExists,
                "Expected AlreadyExists but got $secondResult")
        }

    @Test
    fun savedLinkIsInserted() =
        runBlocking {
            val (saver, prefRepo, linkRepository) = setup()
            prefRepo.setSilentSaveProfileId(1L)

            val result = saver.save("https://example.com", "Example")
            assertTrue(result is SilentSaveResult.Saved, "Expected Saved but got $result")

            val deepr = linkRepository.getDeeprByLink("https://example.com")
            assertEquals("https://example.com", deepr?.link)
            assertEquals(1L, deepr?.profileId)
        }

    private data class Fixture(
        val saver: SilentSaver,
        val preferenceRepository: PreferenceRepository,
        val linkRepository: LinkRepository,
    )

    private suspend fun setup(): Fixture {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY,
            Properties().apply { put("foreign_keys", "true") })
        DeeprDB.Schema.create(driver)
        val queries = DeeprDB(driver).deeprQueries
        val linkRepository = LinkRepositoryImpl(queries)

        // Insert a default profile so insertDeepr foreign key works
        withContext(Dispatchers.IO) {
            linkRepository.insertProfile("Default")
        }

        val htmlParser = HtmlParser()
        // No real network in tests: metadata fetch fails, the link is still saved.
        val networkRepository = NetworkRepository(htmlParser, HttpClient(MockEngine { respondError(HttpStatusCode.NotFound) }))
        // Use unique subdirectory per process
        val prefFile =
            File("build/tmp/SilentSaverTest_${System.identityHashCode(this)}/app_data_store.preferences_pb").apply {
                parentFile?.mkdirs()
                delete()
            }
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { prefFile })
        val preferenceRepository = AppPreferenceDataStore(dataStore)
        val saver = SilentSaver(linkRepository, networkRepository, preferenceRepository)
        return Fixture(saver, preferenceRepository, linkRepository)
    }
}
