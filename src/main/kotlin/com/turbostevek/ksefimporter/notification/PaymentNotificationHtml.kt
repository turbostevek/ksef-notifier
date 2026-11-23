package com.turbostevek.ksefimporter.notification

import com.turbostevek.ksefimporter.history.PurchaseInvoice

/** Renders the HTML body of the batch notification about invoices to pay. */
internal object PaymentNotificationHtml {

    fun render(invoices: List<PurchaseInvoice>): String =
        buildString {
            append("<p>Nowe faktury zakupowe do zapłaty:</p>")
            append("<table border=\"1\" cellspacing=\"0\" cellpadding=\"4\">")
            append("<tr>")
            TABLE_HEADERS.forEach { append("<th>${escape(it)}</th>") }
            append("</tr>")
            invoices.forEach { invoice ->
                append("<tr>")
                rowCells(invoice).forEach { append("<td>${escape(it)}</td>") }
                append("</tr>")
            }
            append("</table>")
        }

    private val TABLE_HEADERS = listOf(
        "Data wystawienia",
        "Numer KSeF",
        "Sprzedawca",
        "Adres",
        "Forma płatności",
        "Numer rachunku",
        "Kwota do zapłaty",
        "Termin płatności",
    )

    private fun rowCells(invoice: PurchaseInvoice): List<String> = listOf(
        invoice.issueDate.toString(),
        invoice.ksefNumber,
        invoice.seller.name ?: "brak danych",
        invoice.seller.address ?: "brak danych",
        invoice.payment.form?.label ?: "brak danych",
        bankAccountCell(invoice),
        "${invoice.grossAmount.toPlainString()} ${invoice.currency}",
        invoice.dueDate?.toString() ?: "brak terminu",
    )

    private fun bankAccountCell(invoice: PurchaseInvoice): String {
        val account = invoice.payment.bankAccount ?: return ""
        return invoice.payment.bankName?.let { "$account ($it)" } ?: account
    }

    private fun escape(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
