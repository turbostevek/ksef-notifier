package com.turbostevek.ksefimporter.ksef

import com.turbostevek.ksefimporter.config.ImportConfig
import org.springframework.stereotype.Component
import java.time.Instant

interface InvoiceSource {
    fun download(config: ImportConfig, token: KsefToken, from: Instant, to: Instant): List<InvoiceFile>
}

/** Logs in to KSeF and downloads the purchase invoices for the requested window. */
@Component
class KsefImportService(private val clientFactory: KsefClientFactory) : InvoiceSource {

    override fun download(config: ImportConfig, token: KsefToken, from: Instant, to: Instant): List<InvoiceFile> {
        val client = clientFactory.create(config.ksefEnvironment.apiBaseUrl)
        val accessToken = client.login(token, config.nip)
        return client.downloadInvoices(accessToken, from, to)
    }
}
