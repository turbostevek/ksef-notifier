package com.turbostevek.ksefimporter.ksef

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.turbostevek.ksefimporter.config.Nip
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.json.JsonMapper
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.security.spec.MGF1ParameterSpec
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

/**
 * Testy klienta KSeF względem lokalnego, fałszywego serwera HTTP. Klucz testowy (test-ksef.p12) służy wyłącznie
 * do szyfrowania i odszyfrowywania w testach; nie jest kluczem KSeF.
 */
class KsefClientTest {

    private val mapper = JsonMapper.builder().build()
    private val certificate: X509Certificate
    private val privateKey: PrivateKey
    private lateinit var server: HttpServer
    private lateinit var baseUrl: String

    private var authStatusCode = 200
    private var exportStatusCode = 200
    private var receivedKsefToken: String? = null
    private var exportSymmetricKey: ByteArray? = null
    private var exportIv: ByteArray? = null

    init {
        val keyStore = KeyStore.getInstance("PKCS12")
        javaClass.getResourceAsStream("/ksef/test-ksef.p12").use { keyStore.load(it, PASSWORD) }
        certificate = keyStore.getCertificate(ALIAS) as X509Certificate
        privateKey = keyStore.getKey(ALIAS, PASSWORD) as PrivateKey
    }

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { exchange -> handle(exchange) }
        server.start()
        baseUrl = "http://localhost:${server.address.port}/v2"
    }

    @AfterEach
    fun stopServer() {
        server.stop(0)
    }

    @Test
    fun `loguje token KSeF i pobiera fakture z paczki scalonej z dwoch czesci, pomijajac metadane paczki`() {
        val client = client()

        val accessToken = client.login(KsefToken(TOKEN), NIP)
        val invoices = client.downloadInvoices(accessToken, Instant.now().minusSeconds(86_400), Instant.now())

        assertEquals("access-token", accessToken.value)
        assertEquals("$TOKEN|$TIMESTAMP_MS", receivedKsefToken)
        assertEquals(listOf("faktura.xml"), invoices.map { it.name })
        assertEquals(INVOICE_XML, String(invoices.single().xml, Charsets.UTF_8))
    }

    @Test
    fun `status logowania inny niz sukces konczy sie KsefException`() {
        authStatusCode = 450

        val client = client()

        assertThrows<KsefException> { client.login(KsefToken(TOKEN), NIP) }
    }

    @Test
    fun `eksport zakonczony statusem bledu konczy sie KsefException`() {
        exportStatusCode = 550

        val client = client()

        assertThrows<KsefException> {
            client.downloadInvoices(AccessToken("access-token"), Instant.now().minusSeconds(86_400), Instant.now())
        }
    }

    private fun client() = KsefClient(baseUrl, pollInterval = Duration.ZERO)

    private fun handle(exchange: HttpExchange) {
        val body = exchange.requestBody.readBytes()
        val path = exchange.requestURI.path
        val method = exchange.requestMethod
        when {
            method == "GET" && path == "/v2/security/public-key-certificates" -> respondJson(exchange, certificatesJson())
            method == "POST" && path == "/v2/auth/challenge" -> respondJson(
                exchange,
                mapOf("challenge" to CHALLENGE, "timestamp" to "2026-01-01T00:00:00Z", "timestampMs" to TIMESTAMP_MS, "clientIp" to "127.0.0.1"),
            )
            method == "POST" && path == "/v2/auth/ksef-token" -> {
                val json = mapper.readTree(body)
                receivedKsefToken = String(decryptRsa(Base64.getDecoder().decode(json.path("encryptedToken").asString())), Charsets.UTF_8)
                respondJson(exchange, mapOf("referenceNumber" to AUTH_REFERENCE, "authenticationToken" to mapOf("token" to "auth-token")), 202)
            }
            method == "GET" && path == "/v2/auth/$AUTH_REFERENCE" -> respondJson(
                exchange,
                mapOf("status" to mapOf("code" to authStatusCode, "description" to "status")),
            )
            method == "POST" && path == "/v2/auth/token/redeem" -> respondJson(
                exchange,
                mapOf("accessToken" to mapOf("token" to "access-token"), "refreshToken" to mapOf("token" to "refresh")),
            )
            method == "POST" && path == "/v2/invoices/exports" -> {
                val encryption = mapper.readTree(body).path("encryption")
                exportSymmetricKey = decryptRsa(Base64.getDecoder().decode(encryption.path("encryptedSymmetricKey").asString()))
                exportIv = Base64.getDecoder().decode(encryption.path("initializationVector").asString())
                respondJson(exchange, mapOf("referenceNumber" to EXPORT_REFERENCE), 201)
            }
            method == "GET" && path == "/v2/invoices/exports/$EXPORT_REFERENCE" -> respondJson(
                exchange,
                exportStatusJson(),
            )
            method == "GET" && path.startsWith("/v2/parts/") -> respond(exchange, 200, parts[path.substringAfterLast('/').toInt() - 1])
            else -> respond(exchange, 404, ByteArray(0))
        }
    }

    private val parts: List<ByteArray> by lazy {
        val encrypted = encryptAes(
            zipOf(
                "faktura.xml" to INVOICE_XML.toByteArray(Charsets.UTF_8),
                "_metadata.json" to "{}".toByteArray(Charsets.UTF_8),
            ),
        )
        val half = encrypted.size / 2
        listOf(encrypted.copyOfRange(0, half), encrypted.copyOfRange(half, encrypted.size))
    }

    private fun exportStatusJson(): Map<String, Any> {
        val packageParts = parts.mapIndexed { index, bytes ->
            mapOf(
                "ordinalNumber" to index + 1,
                "partName" to "part${index + 1}",
                "url" to "$baseUrl/parts/${index + 1}",
                "encryptedPartHash" to sha256Base64(bytes),
            )
        }
        return mapOf(
            "status" to mapOf("code" to exportStatusCode, "description" to "status"),
            "package" to mapOf("parts" to packageParts, "isTruncated" to false, "invoiceCount" to 1),
        )
    }

    private fun certificatesJson(): List<Map<String, Any>> {
        val der = Base64.getEncoder().encodeToString(certificate.encoded)
        return listOf(
            mapOf("certificate" to der, "publicKeyId" to KEY_ID, "usage" to listOf("KsefTokenEncryption"), "validTo" to "2099-01-01T00:00:00Z"),
            mapOf("certificate" to der, "publicKeyId" to KEY_ID, "usage" to listOf("SymmetricKeyEncryption"), "validTo" to "2099-01-01T00:00:00Z"),
        )
    }

    private fun decryptRsa(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey, oaepParameters())
        return cipher.doFinal(data)
    }

    private fun encryptAes(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(exportSymmetricKey, "AES"), IvParameterSpec(exportIv))
        return cipher.doFinal(data)
    }

    private fun oaepParameters() = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)

    private fun sha256Base64(data: ByteArray): String =
        Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(data))

    private fun zipOf(vararg files: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun respondJson(exchange: HttpExchange, body: Any, status: Int = 200) {
        respond(exchange, status, mapper.writeValueAsBytes(body))
    }

    private fun respond(exchange: HttpExchange, status: Int, body: ByteArray) {
        exchange.sendResponseHeaders(status, if (body.isEmpty()) -1 else body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    private companion object {
        const val ALIAS = "ksef-test"
        val PASSWORD = "changeit".toCharArray()
        val NIP = Nip("9261051389")
        const val TOKEN = "test-token"
        const val CHALLENGE = "challenge-1"
        const val TIMESTAMP_MS = 1_767_225_600_000L
        const val KEY_ID = "key-1"
        const val AUTH_REFERENCE = "auth-1"
        const val EXPORT_REFERENCE = "export-1"
        const val INVOICE_XML = "<Faktura>test</Faktura>"
    }
}
