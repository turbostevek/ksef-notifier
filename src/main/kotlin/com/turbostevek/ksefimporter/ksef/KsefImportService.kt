package com.turbostevek.ksefimporter.ksef

import com.turbostevek.ksefimporter.config.ImportConfig
import org.springframework.stereotype.Component
import java.time.Instant

/** Logs in to KSeF and downloads the purchase invoices for the configured import window. */
@Component
class KsefImportService(private val clientFactory: KsefClientFactory) {

    fun downloadPurchaseInvoices(config: ImportConfig, token: KsefToken): List<InvoiceFile> {
        val client = clientFactory.create(config.ksefEnvironment.apiBaseUrl)
        val accessToken = client.login(token, config.nip)
        return client.downloadInvoices(accessToken, config.importFrom(), Instant.now())
    }
}
