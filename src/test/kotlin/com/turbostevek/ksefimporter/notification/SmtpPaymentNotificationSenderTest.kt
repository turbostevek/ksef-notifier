package com.turbostevek.ksefimporter.notification

import com.turbostevek.ksefimporter.config.ImportConfig
import com.turbostevek.ksefimporter.config.KsefEnvironment
import com.turbostevek.ksefimporter.config.Nip
import com.turbostevek.ksefimporter.config.SmtpSettings
import com.turbostevek.ksefimporter.history.PurchaseInvoice
import com.turbostevek.ksefimporter.secrets.SecretName
import com.turbostevek.ksefimporter.secrets.SecretStore
import com.turbostevek.ksefimporter.secrets.SecretStoreException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

class SmtpPaymentNotificationSenderTest {

    @Test
    fun `bez hasla SMTP w magazynie sekretow wysylka konczy sie bledem przed polaczeniem`() {
        val sender = SmtpPaymentNotificationSender(secrets = object : SecretStore {
            override fun read(name: SecretName): String? = null
        })
        val config = config(smtpUsername = "user")

        assertThrows(SecretStoreException::class.java) {
            sender.send(config, listOf(invoice(dueDate = null)))
        }
    }

    @Test
    fun `wiersz tabeli ma pola w kolejności data, numer KSeF, sprzedawca, adres, forma, rachunek, kwota, termin`() {
        val body = paymentNotificationBody(
            listOf(
                invoice(
                    dueDate = LocalDate.of(2026, 4, 1),
                    sellerName = "Firma Sp. z o.o.",
                    sellerAddress = "ul. Długa 1, 00-001 Warszawa, PL",
                    paymentForm = "przelew",
                    bankAccount = "PL61109010140000071219812874",
                    bankName = "PKO BP",
                ),
            ),
        )

        assertEquals(
            listOf(
                listOf(
                    "2026-03-10", "A", "Firma Sp. z o.o.", "ul. Długa 1, 00-001 Warszawa, PL", "przelew",
                    "PL61109010140000071219812874 (PKO BP)", "123.45 PLN", "2026-04-01",
                ),
            ),
            rows(body),
        )
    }

    @Test
    fun `faktura bez rachunku ma puste pole rachunku, a bez terminu brak terminu`() {
        val body = paymentNotificationBody(listOf(invoice(dueDate = null, paymentForm = "gotówka")))

        assertEquals("", rows(body).single()[5])
        assertEquals("brak terminu", rows(body).single()[7])
    }

    @Test
    fun `kazda faktura ma osobny wiersz po naglowku`() {
        val body = paymentNotificationBody(
            listOf(
                invoice("A", dueDate = LocalDate.of(2026, 4, 1)),
                invoice("B", dueDate = null, gross = "10.00"),
            ),
        )

        assertEquals(listOf("A", "B"), rows(body).map { it[1] })
        assertEquals(listOf("123.45 PLN", "10.00 PLN"), rows(body).map { it[6] })
    }

    @Test
    fun `mail nie zawiera NIP wystawcy, a tresc jest escapowana w HTML`() {
        val body = paymentNotificationBody(listOf(invoice(dueDate = null, sellerName = "A&B <x>")))

        assertEquals("A&amp;B &lt;x&gt;", rows(body).single()[2])
        assertFalse(body.contains("5342191158"))
    }

    private fun rows(body: String): List<List<String>> =
        body.split("</tr>")
            .filter { "<td>" in it }
            .map { row -> Regex("<td>(.*?)</td>").findAll(row).map { it.groupValues[1] }.toList() }

    private fun config(smtpUsername: String?) = ImportConfig(
        importTime = LocalTime.of(6, 0),
        startDate = LocalDate.of(2026, 1, 1),
        nip = Nip("9261051389"),
        recipientEmail = "ksef@example.com",
        ksefEnvironment = KsefEnvironment.TEST,
        smtp = SmtpSettings(host = "smtp.example.com", port = 587, username = smtpUsername, from = "ksef@example.com"),
    )

    private fun invoice(
        ksefNumber: String = "A",
        dueDate: LocalDate?,
        gross: String = "123.45",
        sellerName: String? = null,
        sellerAddress: String? = null,
        paymentForm: String? = null,
        bankAccount: String? = null,
        bankName: String? = null,
    ) = PurchaseInvoice(
        ksefNumber = ksefNumber,
        sellerNip = "5342191158",
        buyerNip = "9261051389",
        issueDate = LocalDate.of(2026, 3, 10),
        dueDate = dueDate,
        grossAmount = BigDecimal(gross),
        currency = "PLN",
        paidAtIssue = false,
        sellerName = sellerName,
        sellerAddress = sellerAddress,
        paymentForm = paymentForm,
        bankAccount = bankAccount,
        bankName = bankName,
    )
}
