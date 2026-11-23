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
    }

    @Test
    fun `pusty blok platnosci nie jest oplacona i nie ma terminu`() {
        val invoice = parse(platnosc = "")

        assertFalse(invoice.paidAtIssue)
        assertNull(invoice.dueDate)
    }

    @Test
    fun `faktura bez elementu platnosci nie jest oplacona i nie ma terminu`() {
        val invoice = parse(platnosc = null)

        assertFalse(invoice.paidAtIssue)
        assertNull(invoice.dueDate)
    }

    @Test
    fun `z jednym terminem platnosci zapisuje ten termin`() {
        val invoice = parse(platnosc = "<TerminPlatnosci><Termin>2026-05-20</Termin></TerminPlatnosci>")

        assertEquals(LocalDate.of(2026, 5, 20), invoice.dueDate)
    }

    @Test
    fun `z kilkoma terminami platnosci zapisuje najwczesniejszy`() {
        val invoice = parse(
            platnosc = "<TerminPlatnosci><Termin>2026-05-20</Termin></TerminPlatnosci>" +
                "<TerminPlatnosci><Termin>2026-04-15</Termin></TerminPlatnosci>",
        )

        assertEquals(LocalDate.of(2026, 4, 15), invoice.dueDate)
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
    fun `odczytuje nazwe i adres sprzedawcy w jednej linii`() {
        val invoice = parse()

        assertEquals("Sprzedawca Sp. z o.o.", invoice.seller.name)
        assertEquals("ul. Długa 1, 00-001 Warszawa, PL", invoice.seller.address)
    }

    @Test
    fun `znak nowej linii w adresie zamienia na przecinek`() {
        val xml = fa3Xml().toString(Charsets.UTF_8).replace("ul. Długa 1</AdresL1>", "ul. Długa 1\n  lok. 5</AdresL1>")

        val invoice = Fa3InvoiceParser.parse(KSEF_NUMBER, xml.toByteArray(Charsets.UTF_8))

        assertEquals("ul. Długa 1, lok. 5, 00-001 Warszawa, PL", invoice.seller.address)
    }

    @Test
    fun `mapuje kod formy platnosci FA(3) na nazwe`() {
        assertEquals(PaymentForm.Known(PaymentMethod.TRANSFER), parse(platnosc = "<FormaPlatnosci>6</FormaPlatnosci>").payment.form)
        assertEquals(PaymentForm.Known(PaymentMethod.CASH), parse(platnosc = "<FormaPlatnosci>1</FormaPlatnosci>").payment.form)
    }

    @Test
    fun `nieznany kod formy platnosci trafia do typu Unknown z zachowanym kodem`() {
        assertEquals(PaymentForm.Unknown("9"), parse(platnosc = "<FormaPlatnosci>9</FormaPlatnosci>").payment.form)
    }

    @Test
    fun `odczytuje rachunek bankowy i nazwe banku, gdy sa podane`() {
        val invoice = parse(
            platnosc = "<RachunekBankowy><NrRB>PL61109010140000071219812874</NrRB><NazwaBanku>PKO BP</NazwaBanku></RachunekBankowy>",
        )

        assertEquals("PL61109010140000071219812874", invoice.payment.bankAccount)
        assertEquals("PKO BP", invoice.payment.bankName)
    }

    @Test
    fun `bez rachunku bankowego pola rachunku sa puste`() {
        val invoice = parse(platnosc = "<FormaPlatnosci>1</FormaPlatnosci>")

        assertNull(invoice.payment.bankAccount)
        assertNull(invoice.payment.bankName)
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
        platnosc: String? = "",
        currency: String = "PLN",
        gross: String = "123.45",
    ): PurchaseInvoice = Fa3InvoiceParser.parse(KSEF_NUMBER, fa3Xml(platnosc = platnosc, currency = currency, gross = gross))

    private companion object {
        const val KSEF_NUMBER = "1234567890-20260310-ABCDEF-01"
    }
}
