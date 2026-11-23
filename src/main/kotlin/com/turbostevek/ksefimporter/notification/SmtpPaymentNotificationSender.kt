package com.turbostevek.ksefimporter.notification

import com.turbostevek.ksefimporter.config.ImportConfig
import com.turbostevek.ksefimporter.history.PaymentNotificationSender
import com.turbostevek.ksefimporter.history.PurchaseInvoice
import com.turbostevek.ksefimporter.secrets.SecretName
import com.turbostevek.ksefimporter.secrets.SecretStore
import com.turbostevek.ksefimporter.secrets.SecretStoreException
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component

internal fun paymentNotificationSubject(invoiceCount: Int): String =
    "KSeF: faktury do zapłaty ($invoiceCount)"

internal fun paymentNotificationBody(invoices: List<PurchaseInvoice>): String =
    buildString {
        append("<p>Nowe faktury zakupowe do zapłaty:</p>")
        append("<table border=\"1\" cellspacing=\"0\" cellpadding=\"4\">")
        append("<tr>")
        PAYMENT_TABLE_HEADERS.forEach { append("<th>${escapeHtml(it)}</th>") }
        append("</tr>")
        invoices.forEach { invoice ->
            append("<tr>")
            paymentRowCells(invoice).forEach { append("<td>${escapeHtml(it)}</td>") }
            append("</tr>")
        }
        append("</table>")
    }

private val PAYMENT_TABLE_HEADERS = listOf(
    "Data wystawienia",
    "Numer KSeF",
    "Sprzedawca",
    "Adres",
    "Forma płatności",
    "Numer rachunku",
    "Kwota do zapłaty",
    "Termin płatności",
)

private fun paymentRowCells(invoice: PurchaseInvoice): List<String> = listOf(
    invoice.issueDate.toString(),
    invoice.ksefNumber,
    invoice.sellerName ?: "brak danych",
    invoice.sellerAddress ?: "brak danych",
    invoice.paymentForm ?: "brak danych",
    bankAccountCell(invoice),
    "${invoice.grossAmount.toPlainString()} ${invoice.currency}",
    invoice.dueDate?.toString() ?: "brak terminu",
)

private fun bankAccountCell(invoice: PurchaseInvoice): String {
    val account = invoice.bankAccount ?: return ""
    return invoice.bankName?.let { "$account ($it)" } ?: account
}

private fun escapeHtml(text: String): String =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/** Sends the batch notification through the SMTP server from the configuration. The password comes from the secret store. */
@Component
class SmtpPaymentNotificationSender(private val secrets: SecretStore) : PaymentNotificationSender {

    override fun send(config: ImportConfig, invoices: List<PurchaseInvoice>) {
        val smtp = config.smtp
        val mailSender = JavaMailSenderImpl().apply {
            host = smtp.host
            port = smtp.port
            javaMailProperties["mail.smtp.starttls.enable"] = "true"
            javaMailProperties["mail.smtp.starttls.required"] = "true"
            smtp.username?.let { user ->
                username = user
                password = secrets.read(SecretName.SMTP_PASSWORD)
                    ?: throw SecretStoreException("Brak sekretu '${SecretName.SMTP_PASSWORD.qualifiedName}' w magazynie sekretów")
                javaMailProperties["mail.smtp.auth"] = "true"
            }
        }
        val message = mailSender.createMimeMessage()
        MimeMessageHelper(message, false, Charsets.UTF_8.name()).apply {
            setFrom(smtp.from)
            setTo(config.recipientEmail)
            setSubject(paymentNotificationSubject(invoices.size))
            setText(paymentNotificationBody(invoices), true)
        }
        mailSender.send(message)
    }
}
