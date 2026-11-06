package com.yogeshpaliyal.deepr.di

import com.yogeshpaliyal.deepr.p2p.P2pSyncController
import com.yogeshpaliyal.deepr.p2p.http.KtorHttpPost
import com.yogeshpaliyal.deepr.p2p.http.P2pServer
import com.yogeshpaliyal.deepr.p2p.node.P2pNode
import com.yogeshpaliyal.deepr.p2p.node.SecretStore
import com.yogeshpaliyal.deepr.p2p.core.P2pCrypto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

/**
 * Koin module for P2P sync.
 * Each platform calls this with its own [P2pCrypto] and [SecretStore] implementations.
 */
fun p2pModule(crypto: P2pCrypto, secrets: SecretStore) = module {
    val clock: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() }
    single { P2pNode(get(), crypto, secrets, KtorHttpPost(), clock) }
    single { P2pServer(get()) }
    single {
        P2pSyncController(
            get(),
            get(),
            get(),
            CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
}