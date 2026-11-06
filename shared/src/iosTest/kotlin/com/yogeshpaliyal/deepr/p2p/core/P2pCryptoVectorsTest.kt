package com.yogeshpaliyal.deepr.p2p.core

import com.yogeshpaliyal.deepr.p2p.core.P2pCrypto
import com.yogeshpaliyal.deepr.p2p.ios.CryptoKitP2pCrypto

actual fun cryptoUnderTest(): P2pCrypto = CryptoKitP2pCrypto()