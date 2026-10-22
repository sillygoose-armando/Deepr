package com.yogeshpaliyal.deepr.preference

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.yogeshpaliyal.deepr.ui.screens.home.ViewType
import com.yogeshpaliyal.deepr.viewmodel.SortType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class AppPreferenceDataStoreTest {
    @Test
    fun writesAndReadsSettings() =
        runBlocking {
            val file =
                File("build/tmp/AppPreferenceDataStoreTest/app_data_store.preferences_pb").apply {
                    parentFile?.mkdirs()
                    delete()
                }
            val dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
            val repository = AppPreferenceDataStore(dataStore)

            repository.setSortingOrder(SortType.SORT_NAME_ASC)
            repository.setViewType(ViewType.GRID)

            assertEquals(SortType.SORT_NAME_ASC, repository.getSortingOrder.first())
            assertEquals(ViewType.GRID, repository.viewType.first())
            assertEquals(true, repository.getUseLinkBasedIcons.first())
        }
}
