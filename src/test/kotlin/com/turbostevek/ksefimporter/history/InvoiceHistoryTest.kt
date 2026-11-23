package com.turbostevek.ksefimporter.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.math.BigDecimal
import java.nio.file.Path
import java.sql.DriverManager
import java.time.Instant
import java.time.LocalDate

class InvoiceHistoryTest {

    @TempDir
    lateinit var dir: Path

    private val importedUpTo = Instant.parse("2026-04-01T10:00:00Z")

    @Test
    fun `ponowny zapis tej samej faktury nie tworzy duplikatu`() {
        InvoiceHistory(dir.resolve("invoice-history.db")).use { history ->
            assertEquals(1, history.save(listOf(invoice("A")), importedUpTo))
            assertEquals(0, history.save(listOf(invoice("A")), importedUpTo))

            assertEquals(listOf("A"), history.findAll().map { it.ksefNumber })
        }
    }

    @Test
    fun `po oznaczeniu importu granica okna jest zapamietana, a przed pierwszym importem brak`() {
        InvoiceHistory(dir.resolve("invoice-history.db")).use { history ->
            assertNull(history.lastSuccessfulImport())

            history.save(emptyList(), importedUpTo)
            history.markImported(importedUpTo)

            assertEquals(importedUpTo, history.lastSuccessfulImport())
        }
    }

    @Test
    fun `faktura bez terminu zapisuje termin jako NULL`() {
        InvoiceHistory(dir.resolve("invoice-history.db")).use { history ->
            history.save(listOf(invoice("A", dueDate = null)), importedUpTo)

            assertNull(history.findAll().single().dueDate)
        }
    }

    @Test
    fun `zapisuje walute, kwote brutto i flage oplacenia bez zaokraglen`() {
        InvoiceHistory(dir.resolve("invoice-history.db")).use { history ->
            history.save(listOf(invoice("A", gross = "1234.50", currency = "EUR", paid = true)), importedUpTo)

            val stored = history.findAll().single()
            assertEquals(BigDecimal("1234.50"), stored.grossAmount)
            assertEquals("EUR", stored.currency)
            assertEquals(true, stored.paidAtIssue)
        }
    }

    @Test
    fun `zapisuje dane wystawcy i platnosci oraz odczytuje je z historii`() {
        InvoiceHistory(dir.resolve("invoice-history.db")).use { history ->
            history.save(
                listOf(
                    invoice("A").copy(
                        seller = Seller(name = "Firma", address = "ul. Długa 1, Warszawa"),
                        payment = Payment(form = PaymentForm.Known(PaymentMethod.TRANSFER), bankAccount = "PL61109010140000071219812874", bankName = "PKO BP"),
                    ),
                ),
                importedUpTo,
            )

            val stored = history.findAll().single()
            assertEquals(Seller(name = "Firma", address = "ul. Długa 1, Warszawa"), stored.seller)
            assertEquals(Payment(form = PaymentForm.Known(PaymentMethod.TRANSFER), bankAccount = "PL61109010140000071219812874", bankName = "PKO BP"), stored.payment)
        }
    }

    @Test
    fun `baza ze starym schematem dostaje nowe kolumny, a stare wiersze zostaja z pustymi polami`() {
        val path = dir.resolve("old.db")
        DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE TABLE invoice_history (
                        ksef_number TEXT PRIMARY KEY, seller_nip TEXT, buyer_nip TEXT, issue_date TEXT NOT NULL,
                        due_date TEXT, gross_amount TEXT NOT NULL, currency TEXT NOT NULL,
                        paid_at_issue INTEGER NOT NULL, notified INTEGER NOT NULL DEFAULT 0, imported_at TEXT NOT NULL
                    )
                    """,
                )
                statement.executeUpdate(
                    "INSERT INTO invoice_history VALUES ('OLD', '1111111111', '2222222222', '2026-03-10', NULL, '10.00', 'PLN', 0, 0, '2026-04-01T10:00:00Z')",
                )
            }
        }

        InvoiceHistory(path).use { history ->
            val old = history.findAll().single()
            assertEquals("OLD", old.ksefNumber)
            assertNull(old.seller.name)

            history.save(listOf(invoice("NEW").copy(seller = Seller(name = "Firma"))), importedUpTo)

            assertEquals("Firma", history.findAll().single { it.ksefNumber == "NEW" }.seller.name)
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
