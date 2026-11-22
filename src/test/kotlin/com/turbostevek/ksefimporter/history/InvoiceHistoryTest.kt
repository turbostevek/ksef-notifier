package com.turbostevek.ksefimporter.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.math.BigDecimal
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate

class InvoiceHistoryTest {

    @TempDir
    lateinit var dir: Path

    private val importedUpTo = Instant.parse("2026-04-01T10:00:00Z")

    @Test
    fun `ponowny zapis tej samej faktury nie tworzy duplikatu`() {
        InvoiceHistory(dir.resolve("invoice-history.db")).use { history ->
            assertEquals(1, history.save(listOf(invoice("A")), importedUpTo, importedUpTo))
            assertEquals(0, history.save(listOf(invoice("A")), importedUpTo, importedUpTo))

            assertEquals(listOf("A"), history.findAll().map { it.ksefNumber })
        }
    }

    @Test
    fun `po zapisie importu granica okna jest zapamietana, a przed pierwszym importem brak`() {
        InvoiceHistory(dir.resolve("invoice-history.db")).use { history ->
            assertNull(history.lastSuccessfulImport())

            history.save(emptyList(), importedUpTo, importedUpTo)

            assertEquals(importedUpTo, history.lastSuccessfulImport())
        }
    }

    @Test
    fun `faktura bez terminu zapisuje termin jako NULL`() {
        InvoiceHistory(dir.resolve("invoice-history.db")).use { history ->
            history.save(listOf(invoice("A", dueDate = null)), importedUpTo, importedUpTo)

            assertNull(history.findAll().single().dueDate)
        }
    }

    @Test
    fun `zapisuje walute, kwote brutto i flage oplacenia bez zaokraglen`() {
        InvoiceHistory(dir.resolve("invoice-history.db")).use { history ->
            history.save(listOf(invoice("A", gross = "1234.50", currency = "EUR", paid = true)), importedUpTo, importedUpTo)

            val stored = history.findAll().single()
            assertEquals(BigDecimal("1234.50"), stored.grossAmount)
            assertEquals("EUR", stored.currency)
            assertEquals(true, stored.paidAtIssue)
        }
    }

    private fun invoice(
        ksefNumber: String,
        dueDate: LocalDate? = LocalDate.of(2026, 4, 30),
        gross: String = "100.00",
        currency: String = "PLN",
        paid: Boolean = false,
    ) = PurchaseInvoice(
        ksefNumber = ksefNumber,
        sellerNip = "1111111111",
        buyerNip = "2222222222",
        issueDate = LocalDate.of(2026, 3, 10),
        dueDate = dueDate,
        grossAmount = BigDecimal(gross),
        currency = currency,
        paidAtIssue = paid,
    )
}
