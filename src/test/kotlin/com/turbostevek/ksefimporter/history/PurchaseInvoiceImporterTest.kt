package com.turbostevek.ksefimporter.history

import com.turbostevek.ksefimporter.config.ImportConfig
import com.turbostevek.ksefimporter.config.ImportConfigLoader
import com.turbostevek.ksefimporter.ksef.InvoiceFile
import com.turbostevek.ksefimporter.ksef.InvoiceSource
import com.turbostevek.ksefimporter.ksef.KsefException
import com.turbostevek.ksefimporter.ksef.KsefToken
import com.turbostevek.ksefimporter.validConfigYaml
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant

class PurchaseInvoiceImporterTest {

    @TempDir
    lateinit var dir: Path

    private val config: ImportConfig = ImportConfigLoader().parse(validConfigYaml)
    private val token = KsefToken("token")
    private val historyPath: Path get() = dir.resolve("invoice-history.db")
    private val source = FakeInvoiceSource()
    private val notifier = FakePaymentNotificationSender()
    private val importer = PurchaseInvoiceImporter(source, notifier)

    @Test
    fun `jedno zbiorcze powiadomienie zawiera wszystkie nowe faktury do zaplaty z przebiegu`() {
        source.files = listOf(
            invoiceFile("A", buyerNip = TEST_NIP),
            invoiceFile("B", buyerNip = TEST_NIP),
        )

        importer.importInvoices(config, token, historyPath)

        assertEquals(listOf("A", "B"), notifier.sent.single().map { it.ksefNumber })
    }

    @Test
    fun `bez nowych faktur do zaplaty nie wysyla sie zadne powiadomienie`() {
        source.files = listOf(invoiceFile("PAID", buyerNip = TEST_NIP, platnosc = "<Zaplacono>1</Zaplacono>"))

        importer.importInvoices(config, token, historyPath)

        assertTrue(notifier.sent.isEmpty())
    }

    @Test
    fun `faktura oplacona przy wystawieniu nie trafia do maila`() {
        source.files = listOf(
            invoiceFile("PAID", buyerNip = TEST_NIP, platnosc = "<Zaplacono>1</Zaplacono>"),
            invoiceFile("OPEN", buyerNip = TEST_NIP),
        )

        importer.importInvoices(config, token, historyPath)

        assertEquals(listOf("OPEN"), notifier.sent.single().map { it.ksefNumber })
    }

    @Test
    fun `bledy wysylki nie cofaja importu i kolejny przebieg wysyla zaleglosc raz`() {
        source.files = listOf(invoiceFile("A", buyerNip = TEST_NIP))
        notifier.failure = IllegalStateException("SMTP niedostępny")

        val first = importer.importInvoices(config, token, historyPath)
        assertEquals(1, first.added)
        assertTrue(notifier.sent.isEmpty())
        assertEquals(listOf("A"), InvoiceHistory(historyPath).use { it.unnotifiedToPay().map { invoice -> invoice.ksefNumber } })

        notifier.failure = null
        importer.importInvoices(config, token, historyPath)
        importer.importInvoices(config, token, historyPath)

        assertEquals(listOf(listOf("A")), notifier.sent.map { mail -> mail.map { it.ksefNumber } })
        assertTrue(InvoiceHistory(historyPath).use { it.unnotifiedToPay() }.isEmpty())
    }

    @Test
    fun `pierwszy import pobiera faktury od daty startDate z konfiguracji`() {
        importer.importInvoices(config, token, historyPath)

        assertEquals(config.importFrom(), source.windows.single().first)
    }

    @Test
    fun `kolejny import zaczyna od konca poprzedniego udanego okna`() {
        importer.importInvoices(config, token, historyPath)
        importer.importInvoices(config, token, historyPath)

        val (firstFrom, firstTo) = source.windows[0]
        val (secondFrom, _) = source.windows[1]
        assertEquals(config.importFrom(), firstFrom)
        assertEquals(firstTo, secondFrom)
    }

    @Test
    fun `do historii trafiaja tylko faktury, w ktorych nabywca to NIP z konfiguracji`() {
        source.files = listOf(
            invoiceFile("PURCHASE", buyerNip = TEST_NIP),
            invoiceFile("SALE", buyerNip = "1111111111"),
        )

        val result = importer.importInvoices(config, token, historyPath)

        assertEquals(1, result.downloaded)
        assertEquals(1, result.added)
        assertEquals(listOf("PURCHASE"), InvoiceHistory(historyPath).use { it.findAll().map { invoice -> invoice.ksefNumber } })
    }

    @Test
    fun `ponowny import tych samych faktur nie tworzy duplikatow`() {
        source.files = listOf(invoiceFile("A", buyerNip = TEST_NIP))

        importer.importInvoices(config, token, historyPath)
        val second = importer.importInvoices(config, token, historyPath)

        assertEquals(0, second.added)
        assertEquals(1, InvoiceHistory(historyPath).use { it.findAll().size })
    }

    @Test
    fun `nieudane pobieranie nie przesuwa granicy importu`() {
        source.failure = KsefException("KSeF niedostępny")
        assertThrows<KsefException> { importer.importInvoices(config, token, historyPath) }

        source.failure = null
        importer.importInvoices(config, token, historyPath)

        assertEquals(config.importFrom(), source.windows.last().first)
    }

    private fun invoiceFile(ksefNumber: String, buyerNip: String, platnosc: String = ""): InvoiceFile =
        InvoiceFile(ksefNumber, fa3Xml(buyerNip = buyerNip, platnosc = platnosc))

    private class FakePaymentNotificationSender : PaymentNotificationSender {
        val sent = mutableListOf<List<PurchaseInvoice>>()
        var failure: RuntimeException? = null

        override fun send(config: ImportConfig, invoices: List<PurchaseInvoice>) {
            failure?.let { throw it }
            sent += invoices
        }
    }

    private class FakeInvoiceSource : InvoiceSource {
        var files: List<InvoiceFile> = emptyList()
        var failure: RuntimeException? = null
        val windows = mutableListOf<Pair<Instant, Instant>>()

        override fun download(config: ImportConfig, token: KsefToken, from: Instant, to: Instant): List<InvoiceFile> {
            windows += from to to
            failure?.let { throw it }
            return files
        }
    }
}
