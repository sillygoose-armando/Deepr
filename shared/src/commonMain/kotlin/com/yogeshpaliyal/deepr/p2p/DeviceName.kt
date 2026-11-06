package com.yogeshpaliyal.deepr.p2p

/**
 * Returns a human-readable default name for this device, used when [SyncDevice.deviceName]
 * is still blank. Each platform provides its own implementation.
 */
expect fun defaultDeviceName(): String