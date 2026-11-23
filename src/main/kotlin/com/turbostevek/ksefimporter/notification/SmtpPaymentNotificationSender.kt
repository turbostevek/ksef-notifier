package com.turbostevek.ksefimporter.notification

import com.turbostevek.ksefimporter.config.ImportConfig
import com.turbostevek.ksefimporter.history.PaymentNotificationSender
import com.turbostevek.ksefimporter.history.PurchaseInvoice
import com.turbostevek.ksefimporter.secrets.SecretName
import com.turbostevek.ksefimporter.secrets.SecretStore
import com.turbostevek.ksefimporter.secrets.SecretStoreException
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.stereotype.Component

internal fun paymentNotificationSubject(invoiceCount: Int): String =
    "KSeF: faktury do zapłaty ($invoiceCount)"

internal fun paymentNotificationBody(invoices: List<PurchaseInvoice>): String =
    buildString {
        appendLine("Nowe faktury zakupowe do zapłaty:")
        appendLine()
        invoices.forEach { invoice ->
            appendLine(invoiceLine(invoice))
            appendLine("  wystawca: ${invoice.sellerName ?: "brak danych"}, ${invoice.sellerAddress ?: "brak adresu"}")
            appendLine("  płatność: ${paymentDetails(invoice)}")
        }
    }.trimEnd()

private fun invoiceLine(invoice: PurchaseInvoice): String {
    val dueDate = invoice.dueDate?.toString() ?: "brak terminu"
    return "- ${invoice.issueDate}, nr KSeF ${invoice.ksefNumber}, " +
        "${invoice.grossAmount.toPlainString()} ${invoice.currency}, termin: $dueDate"
}

private fun paymentDetails(invoice: PurchaseInvoice): String {
    val form = invoice.paymentForm ?: "brak danych"
    val account = invoice.bankAccount ?: return form
    val bank = invoice.bankName?.let { " ($it)" } ?: ""
    return "$form, rachunek $account$bank"
}

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
        mailSender.send(
            SimpleMailMessage().apply {
                from = smtp.from
                setTo(config.recipientEmail)
                subject = paymentNotificationSubject(invoices.size)
                text = paymentNotificationBody(invoices)
            },
        )
    }
}
