package com.yogeshpaliyal.deepr.di

import com.yogeshpaliyal.deepr.DeeprDB
import com.yogeshpaliyal.deepr.p2p.P2pSyncController
import com.yogeshpaliyal.deepr.p2p.http.KtorHttpPost
import com.yogeshpaliyal.deepr.p2p.http.P2pServer
import com.yogeshpaliyal.deepr.p2p.node.IrohKeyStore
import com.yogeshpaliyal.deepr.p2p.node.IrohLink
import com.yogeshpaliyal.deepr.p2p.node.P2pNode
import com.yogeshpaliyal.deepr.p2p.node.SecretStore
import com.yogeshpaliyal.deepr.p2p.core.P2pCrypto
import com.yogeshpaliyal.deepr.viewmodel.SyncViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Koin module for P2P sync.
 * Each platform calls this with its own [P2pCrypto] and [SecretStore] implementations.
 *
 * @param irohFactory  builds the platform's iroh transport (JVM only). It receives the node, the
 *   [IrohKeyStore] backed by `SyncDevice.irohKeyEnc`, and the module's coroutine scope. iOS passes
 *   `null` until M5-10.
 */
fun p2pModule(
    crypto: P2pCrypto,
    secrets: SecretStore,
    irohFactory: ((P2pNode, IrohKeyStore, CoroutineScope) -> IrohLink)? = null,
) = module {
    val clock: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    single {
        val db = get<DeeprDB>()
        val node = P2pNode(db, crypto, secrets, KtorHttpPost(), clock)
        irohFactory?.let { node.iroh = it(node, IrohKeyStore(db, secrets), scope) }
        node
    }
    single { P2pServer(get()) }
    single { P2pSyncController(get(), get(), get(), scope) }
    viewModelOf(::SyncViewModel)
}
