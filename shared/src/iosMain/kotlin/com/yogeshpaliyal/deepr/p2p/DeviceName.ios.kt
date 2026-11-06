@file:OptIn(ExperimentalForeignApi::class)

package com.yogeshpaliyal.deepr.p2p

import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIDevice

actual fun defaultDeviceName(): String = UIDevice.currentDevice.name