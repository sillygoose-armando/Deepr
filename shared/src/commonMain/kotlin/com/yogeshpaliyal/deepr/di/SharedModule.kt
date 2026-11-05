package com.yogeshpaliyal.deepr.di

import app.cash.sqldelight.db.SqlDriver
import com.yogeshpaliyal.deepr.DeeprDB
import com.yogeshpaliyal.deepr.DeeprQueries
import com.yogeshpaliyal.deepr.data.HtmlParser
import com.yogeshpaliyal.deepr.data.NetworkRepository
import com.yogeshpaliyal.deepr.data.SilentSaver
import com.yogeshpaliyal.deepr.viewmodel.AccountViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val sharedModule = module {
    single { DeeprDB(get()) }
    single<DeeprQueries> { get<DeeprDB>().deeprQueries }
    single { HtmlParser() }
    single { NetworkRepository(get()) }
    single { SilentSaver(get(), get(), get()) }
    viewModelOf(::AccountViewModel)
}
