package com.yogeshpaliyal.deepr.desktop

import com.yogeshpaliyal.deepr.DeeprQueries
import com.yogeshpaliyal.deepr.data.LinkRepository
import com.yogeshpaliyal.deepr.di.sharedModule
import com.yogeshpaliyal.deepr.di.unsupportedFeaturesModule
import com.yogeshpaliyal.deepr.preference.PreferenceRepository
import com.yogeshpaliyal.deepr.viewmodel.AccountViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.inject
import java.io.File

class DesktopModuleTest : KoinTest {
    private val tmpDir: File =
        // Not deleted in @After: AccountViewModel\'s init coroutine may still write to the DB after the test body (SQLITE_IOERR_DELETE_NOENT). `gradle clean` removes it.
        File("build/tmp/deepr-desktop-test-${System.nanoTime()}")

    private val deeprQueries: DeeprQueries by inject()
    private val preferenceRepository: PreferenceRepository by inject()
    private val linkRepository: LinkRepository by inject()
    private val accountViewModel: AccountViewModel by inject()

    @Before
    fun setUp() {
        tmpDir.mkdirs()
        stopKoin()
        startKoin {
            modules(sharedModule, desktopModule(tmpDir), unsupportedFeaturesModule("desktop"))
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `start Koin and resolve all dependencies`() = runBlocking {
        // Prove the whole graph resolves on desktop
        val vm = accountViewModel

        // Insert a profile and read it back
        val profileName = "Desktop Test Profile"
        linkRepository.insertProfile(profileName)
        val profiles = linkRepository.getAllProfilesOnce()
        assert(profiles.any { it.name == profileName }) {
            "Expected profile '$profileName' to exist"
        }
    }
}
