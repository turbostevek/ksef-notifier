package com.turbostevek.ksefimporter.history

import com.turbostevek.ksefimporter.ksef.KsefException
import org.w3c.dom.Document
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPath
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

/**
 * Reads the fields of a FA(3) invoice XML. Elements are matched by local name, so the parser does not depend
 * on the namespace version of the schema. The KSeF number is not part of the XML and is passed in by the caller.
 */
object Fa3InvoiceParser {

    fun parse(ksefNumber: String, xml: ByteArray): PurchaseInvoice {
        val document = try {
            secureDocumentBuilder().parse(ByteArrayInputStream(xml))
        } catch (e: Exception) {
            throw KsefException("Nie można odczytać faktury $ksefNumber: ${e.message}", e)
        }
        val xpath = XPathFactory.newInstance().newXPath()

        fun text(vararg names: String): String? =
            xpath.evaluate(path(*names), document).trim().ifEmpty { null }

        fun required(name: String, vararg names: String): String =
            text(*names) ?: throw KsefException("Faktura $ksefNumber nie zawiera pola $name")

        return try {
            PurchaseInvoice(
                ksefNumber = ksefNumber,
                sellerNip = text("Podmiot1", "DaneIdentyfikacyjne", "NIP"),
                buyerNip = text("Podmiot2", "DaneIdentyfikacyjne", "NIP"),
                issueDate = LocalDate.parse(required("P_1", "Fa", "P_1")),
                dueDate = dueDate(xpath, document),
                grossAmount = BigDecimal(required("P_15", "Fa", "P_15")),
                currency = required("KodWaluty", "Fa", "KodWaluty"),
                paidAtIssue = text("Fa", "Platnosc", "Zaplacono") == PAID_FLAG,
                sellerName = text("Podmiot1", "DaneIdentyfikacyjne", "Nazwa"),
                sellerAddress = listOfNotNull(
                    text("Podmiot1", "Adres", "AdresL1"),
                    text("Podmiot1", "Adres", "AdresL2"),
                    text("Podmiot1", "Adres", "KodKraju"),
                ).joinToString(", ").ifEmpty { null },
                paymentForm = text("Fa", "Platnosc", "FormaPlatnosci")?.let { PAYMENT_FORMS[it] ?: it },
                bankAccount = text("Fa", "Platnosc", "RachunekBankowy", "NrRB"),
                bankName = text("Fa", "Platnosc", "RachunekBankowy", "NazwaBanku"),
            )
        } catch (e: DateTimeParseException) {
            throw KsefException("Faktura $ksefNumber ma nieprawidłową datę: ${e.parsedString}", e)
        } catch (e: NumberFormatException) {
            throw KsefException("Faktura $ksefNumber ma nieprawidłową kwotę brutto", e)
        }
    }

    /** The earliest payment term is the one that matters for the reminder. */
    private fun dueDate(xpath: XPath, document: Document): LocalDate? {
        val terms: NodeList = xpath.evaluate(path("Fa", "Platnosc", "TerminPlatnosci", "Termin"), document, XPathConstants.NODESET) as NodeList
        return (0 until terms.length)
            .map { LocalDate.parse(terms.item(it).textContent.trim()) }
            .minOrNull()
    }

    private fun path(vararg names: String): String =
        names.joinToString(separator = "/", prefix = "/*[local-name()='Faktura']/") { "*[local-name()='$it']" }

    private fun secureDocumentBuilder() = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isXIncludeAware = false
        isExpandEntityReferences = false
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    }.newDocumentBuilder()

    private const val PAID_FLAG = "1"

    /** Codes of the `FormaPlatnosci` element in FA(3). */
    private val PAYMENT_FORMS = mapOf(
        "1" to "gotówka",
        "2" to "karta",
        "3" to "bon",
        "4" to "czek",
        "5" to "kredyt",
        "6" to "przelew",
        "7" to "mobilna",
    )
}
