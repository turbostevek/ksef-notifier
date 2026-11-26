package com.turbostevek.ksefimporter.notification

import com.turbostevek.ksefimporter.config.ConfigException
import com.turbostevek.ksefimporter.config.ImportConfig
import com.turbostevek.ksefimporter.history.PaymentNotificationSender
import com.turbostevek.ksefimporter.history.PurchaseInvoice
import com.turbostevek.ksefimporter.secrets.SecretName
import com.turbostevek.ksefimporter.secrets.SecretStore
import com.turbostevek.ksefimporter.secrets.SecretStoreException
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

internal fun paymentNotificationSubject(invoiceCount: Int): String =
    "KSeF: faktury do zapłaty ($invoiceCount)"

/** Sends the batch notification through the SMTP server from the configuration. The password comes from the secret store. */
@Component
class SmtpPaymentNotificationSender(private val secrets: SecretStore) : PaymentNotificationSender {

    override fun send(config: ImportConfig, invoices: List<PurchaseInvoice>) {
        val mailSender = createMailSender(config)
        val message = mailSender.createMimeMessage()
        MimeMessageHelper(message, false, Charsets.UTF_8.name()).apply {
            setFrom(config.smtp.from)
            setTo(config.recipientEmail)
            setSubject(paymentNotificationSubject(invoices.size))
            setText(PaymentNotificationHtml.render(invoices), true)
        }
        mailSender.send(message)
    }

    fun checkConnection(config: ImportConfig) {
        createMailSender(config).testConnection()
    }

    private fun createMailSender(config: ImportConfig): JavaMailSenderImpl {
        val smtp = config.smtp
        return JavaMailSenderImpl().apply {
            host = smtp.host
            port = smtp.port
            javaMailProperties["mail.smtp.starttls.enable"] = "true"
            javaMailProperties["mail.smtp.starttls.required"] = "true"
            smtp.caCertificate?.let { javaMailProperties["mail.smtp.ssl.socketFactory"] = trustingSocketFactory(it) }
            smtp.username?.let { user ->
                username = user
                password = secrets.read(SecretName.SMTP_PASSWORD)
                    ?: throw SecretStoreException("Brak sekretu '${SecretName.SMTP_PASSWORD.qualifiedName}' w magazynie sekretów")
                javaMailProperties["mail.smtp.auth"] = "true"
            }
        }
    }

    /** Default JDK trust anchors plus [caCertificate]. The system `cacerts` file is not modified. */
    private fun trustingSocketFactory(caCertificate: Path): SSLSocketFactory {
        val extraCa = try {
            Files.newInputStream(caCertificate).use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
        } catch (e: IOException) {
            throw ConfigException("Nie można odczytać certyfikatu smtp.caCertificate $caCertificate: ${e.message}")
        } catch (e: CertificateException) {
            throw ConfigException("Nieprawidłowy certyfikat smtp.caCertificate $caCertificate: ${e.message}")
        }
        val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            val defaultManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply { init(null as KeyStore?) }
            defaultManager.trustManagers.filterIsInstance<X509TrustManager>()
                .flatMap { it.acceptedIssuers.toList() }
                .forEachIndexed { index, certificate -> setCertificateEntry("jdk-$index", certificate) }
            setCertificateEntry("smtp-extra-ca", extraCa)
        }
        val trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(trustStore) }
            .trustManagers
        return SSLContext.getInstance("TLS").apply { init(null, trustManagers, null) }.socketFactory
    }
}
