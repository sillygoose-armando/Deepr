package com.yogeshpaliyal.deepr.p2p

import android.os.Build

actual fun defaultDeviceName(): String = Build.MANUFACTURER + " " + Build.MODEL