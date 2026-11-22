package com.turbostevek.ksefimporter.history

import com.turbostevek.ksefimporter.config.ImportConfig
import com.turbostevek.ksefimporter.ksef.InvoiceSource
import com.turbostevek.ksefimporter.ksef.KsefToken
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.time.Instant

data class ImportResult(val downloaded: Int, val added: Int)

/**
 * Incremental import of purchase invoices into the local history. The first run starts at the configured start date,
 * later runs start where the last successful run ended.
 */
@Component
class PurchaseInvoiceImporter(private val source: InvoiceSource) {

    fun importInvoices(config: ImportConfig, token: KsefToken, historyPath: Path): ImportResult =
        InvoiceHistory(historyPath).use { history ->
            val now = Instant.now()
            val from = history.lastSuccessfulImport() ?: config.importFrom()
            val purchases = source.download(config, token, from, now)
                .map { Fa3InvoiceParser.parse(it.ksefNumber, it.xml) }
                .filter { it.buyerNip == config.nip.value }
            val added = history.save(purchases, importedUpTo = now, importedAt = now)
            ImportResult(downloaded = purchases.size, added = added)
        }
}
