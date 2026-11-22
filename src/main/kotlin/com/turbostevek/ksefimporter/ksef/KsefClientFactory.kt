package com.turbostevek.ksefimporter.ksef

import org.springframework.stereotype.Component

@Component
class KsefClientFactory {

    fun create(apiBaseUrl: String): KsefClient = KsefClient(apiBaseUrl)
}
