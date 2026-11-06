package com.yogeshpaliyal.deepr.p2p.core

import com.yogeshpaliyal.deepr.p2p.core.P2pCrypto
import com.yogeshpaliyal.deepr.p2p.jvm.JvmP2pCrypto

actual fun cryptoUnderTest(): P2pCrypto = JvmP2pCrypto()