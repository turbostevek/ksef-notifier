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
    fun `faktura bez terminu platnosci jest oznaczona jako brak terminu`() {
        val body = paymentNotificationBody(listOf(invoice(dueDate = null)))

        assertEquals(
            "- 2026-03-10, sprzedawca 5342191158, nr KSeF A, 123.45 PLN, termin: brak terminu",
            body.lines().last(),
        )
    }

    @Test
    fun `kazda faktura w osobnej linii z kwota, waluta i terminem`() {
        val body = paymentNotificationBody(
            listOf(
                invoice("A", dueDate = LocalDate.of(2026, 4, 1)),
                invoice("B", dueDate = null, gross = "10.00"),
            ),
        )

        assertEquals(
            listOf(
                "- 2026-03-10, sprzedawca 5342191158, nr KSeF A, 123.45 PLN, termin: 2026-04-01",
                "- 2026-03-10, sprzedawca 5342191158, nr KSeF B, 10.00 PLN, termin: brak terminu",
            ),
            body.lines().filter { it.startsWith("- ") },
        )
    }

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
    ) = PurchaseInvoice(
        ksefNumber = ksefNumber,
        sellerNip = "5342191158",
        buyerNip = "9261051389",
        issueDate = LocalDate.of(2026, 3, 10),
        dueDate = dueDate,
        grossAmount = BigDecimal(gross),
        currency = "PLN",
        paidAtIssue = false,
    )
}
