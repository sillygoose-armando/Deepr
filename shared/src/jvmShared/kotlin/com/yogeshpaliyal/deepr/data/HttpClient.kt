package com.yogeshpaliyal.deepr.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

internal actual fun defaultHttpClient(): HttpClient = HttpClient(CIO)
