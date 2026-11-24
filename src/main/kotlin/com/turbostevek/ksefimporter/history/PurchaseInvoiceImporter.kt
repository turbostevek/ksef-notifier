package com.turbostevek.ksefimporter.history

import com.turbostevek.ksefimporter.config.ImportConfig
import com.turbostevek.ksefimporter.ksef.InvoiceSource
import com.turbostevek.ksefimporter.ksef.KsefToken
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.time.Instant

data class ImportResult(val downloaded: Int, val added: Int, val notificationFailed: Boolean = false)

/**
 * Incremental import of purchase invoices into the local history. The first run starts at the configured start date,
 * later runs start where the last successful run ended. After the import, one batch notification is sent about
 * invoices to pay that were not notified yet. The import state moves only when the notification succeeds (or nothing
 * needed sending), so a failed notification keeps the invoices and the window open: it is reported in
 * [ImportResult.notificationFailed], and the next run re-downloads the window and retries the notification.
 */
@Component
class PurchaseInvoiceImporter(
    private val source: InvoiceSource,
    private val notificationSender: PaymentNotificationSender,
) {

    private val log = LoggerFactory.getLogger(PurchaseInvoiceImporter::class.java)

    fun importInvoices(config: ImportConfig, token: KsefToken, historyPath: Path): ImportResult =
        InvoiceHistory(historyPath).use { history ->
            val now = Instant.now()
            val from = history.lastSuccessfulImport() ?: config.importFrom()
            val purchases = source.download(config, token, from, now)
                .map { Fa3InvoiceParser.parse(it.ksefNumber, it.xml) }
                .filter { it.buyerNip == config.nip.value }
            val added = history.save(purchases, importedAt = now)
            val notificationFailed = notifyAboutInvoicesToPay(config, history)
            if (!notificationFailed) history.markImported(now)
            ImportResult(downloaded = purchases.size, added = added, notificationFailed = notificationFailed)
        }

    /** Returns true when the notification was attempted and failed. */
    private fun notifyAboutInvoicesToPay(config: ImportConfig, history: InvoiceHistory): Boolean {
        val toPay = history.unnotifiedToPay()
        if (toPay.isEmpty()) return false
        if (sendFailed(config, toPay)) return true
        history.markNotified(toPay.map { it.ksefNumber })
        return false
    }

    /**
     * Returns true when the delivery failed, so the invoices stay unnotified. Errors while marking the invoices
     * after a successful delivery are not caught here: they propagate and the next run may notify them again.
     */
    private fun sendFailed(config: ImportConfig, toPay: List<PurchaseInvoice>): Boolean =
        try {
            notificationSender.send(config, toPay)
            false
        } catch (e: Exception) {
            log.error("Nie udało się wysłać powiadomienia o ${toPay.size} fakturach do zapłaty, ponowienie w kolejnym przebiegu", e)
            true
        }
}
