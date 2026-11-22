package com.turbostevek.ksefimporter.history

import com.turbostevek.ksefimporter.config.ImportConfig
import com.turbostevek.ksefimporter.config.ImportConfigLoader
import com.turbostevek.ksefimporter.ksef.InvoiceFile
import com.turbostevek.ksefimporter.ksef.InvoiceSource
import com.turbostevek.ksefimporter.ksef.KsefException
import com.turbostevek.ksefimporter.ksef.KsefToken
import com.turbostevek.ksefimporter.validConfigYaml
import org.junit.jupiter.api.Assertions.assertEquals
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
    private val historyPath: Path get() = dir.resolve("historia.db")
    private val source = FakeInvoiceSource()
    private val importer = PurchaseInvoiceImporter(source)

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

        assertEquals(1, result.purchases)
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

    private fun invoiceFile(ksefNumber: String, buyerNip: String): InvoiceFile =
        InvoiceFile("$ksefNumber.xml", fa3Xml(buyerNip = buyerNip))

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
