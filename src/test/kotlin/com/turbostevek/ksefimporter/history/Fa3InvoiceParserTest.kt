package com.turbostevek.ksefimporter.history

import com.turbostevek.ksefimporter.ksef.KsefException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class Fa3InvoiceParserTest {

    @Test
    fun `faktura oplacona przy wystawieniu ma flage oplacona i brak terminu`() {
        val invoice = parse(platnosc = "<Zaplacono>1</Zaplacono><DataZaplaty>2026-03-10</DataZaplaty>")

        assertTrue(invoice.paidAtIssue)
        assertNull(invoice.dueDate)
        assertEquals("brak terminu", invoice.dueDateLabel)
    }

    @Test
    fun `faktura bez bloku platnosci nie jest oplacona i nie ma terminu`() {
        val invoice = parse(platnosc = "")

        assertFalse(invoice.paidAtIssue)
        assertNull(invoice.dueDate)
    }

    @Test
    fun `z kilkoma terminami platnosci zapisuje najwczesniejszy`() {
        val invoice = parse(
            platnosc = "<TerminPlatnosci><Termin>2026-05-20</Termin></TerminPlatnosci>" +
                "<TerminPlatnosci><Termin>2026-04-15</Termin></TerminPlatnosci>",
        )

        assertEquals(LocalDate.of(2026, 4, 15), invoice.dueDate)
        assertEquals("2026-04-15", invoice.dueDateLabel)
    }

    @Test
    fun `faktura w walucie innej niz PLN zapisuje walute i kwote brutto`() {
        val invoice = parse(currency = "EUR", gross = "1234.50")

        assertEquals("EUR", invoice.currency)
        assertEquals(BigDecimal("1234.50"), invoice.grossAmount)
    }

    @Test
    fun `odczytuje NIP sprzedawcy i nabywcy oraz date wystawienia`() {
        val invoice = parse()

        assertEquals("1111111111", invoice.sellerNip)
        assertEquals(TEST_NIP, invoice.buyerNip)
        assertEquals(LocalDate.of(2026, 3, 10), invoice.issueDate)
    }

    @Test
    fun `niepoprawny XML konczy sie bledem KSeF`() {
        assertThrows<KsefException> {
            Fa3InvoiceParser.parse(KSEF_NUMBER, "<Faktura>".toByteArray())
        }
    }

    @Test
    fun `XML z deklaracja DOCTYPE jest odrzucany`() {
        val xml = """<?xml version="1.0"?><!DOCTYPE Faktura [<!ENTITY x "y">]><Faktura/>"""

        assertThrows<KsefException> {
            Fa3InvoiceParser.parse(KSEF_NUMBER, xml.toByteArray())
        }
    }

    private fun parse(
        platnosc: String = "",
        currency: String = "PLN",
        gross: String = "123.45",
    ): PurchaseInvoice = Fa3InvoiceParser.parse(KSEF_NUMBER, fa3Xml(platnosc = platnosc, currency = currency, gross = gross))

    private companion object {
        const val KSEF_NUMBER = "1234567890-20260310-ABCDEF-01"
    }
}
