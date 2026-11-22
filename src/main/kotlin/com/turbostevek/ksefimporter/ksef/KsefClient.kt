package com.turbostevek.ksefimporter.ksef

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.Base64
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

data class InvoiceFile(val name: String, val xml: ByteArray)

class KsefException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Klient KSeF API v2: logowanie tokenem KSeF i pobranie faktur zakupowych (nabywca = podmiot 2).
 * Wszystkie dane są przetwarzane w pamięci; pliki faktur nie są zapisywane na dysku.
 */
class KsefClient(
    apiBaseUrl: String,
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(),
    private val pollInterval: Duration = Duration.ofSeconds(1),
    private val maxPolls: Int = 60,
) {
    private val baseUrl = apiBaseUrl.trimEnd('/')
    private val mapper = JsonMapper.builder()
        .addModule(kotlinModule())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()
    private val crypto = KsefCrypto

    /** Zwraca access token do kolejnych wywołań API. */
    fun login(token: String, nip: String): String {
        val tokenCertificate = fetchCertificate(USAGE_TOKEN_ENCRYPTION)
        val challenge = post("/auth/challenge", body = null, operation = "Pobranie challenge")
            .let { readJson<ChallengeResponse>(it, "Pobranie challenge") }
        val encryptedToken = crypto.rsaOaepEncrypt(
            "$token|${challenge.timestampMs}".toByteArray(Charsets.UTF_8),
            tokenCertificate.certificate,
        )
        val init = post(
            "/auth/ksef-token",
            body = mapOf(
                "challenge" to challenge.challenge,
                "contextIdentifier" to mapOf("type" to "Nip", "value" to nip),
                "encryptedToken" to Base64.getEncoder().encodeToString(encryptedToken),
                "publicKeyId" to tokenCertificate.publicKeyId,
            ),
            operation = "Logowanie tokenem KSeF",
        ).let { readJson<AuthInitResponse>(it, "Logowanie tokenem KSeF") }

        val authToken = init.authenticationToken.token
        awaitStatus("Logowanie tokenem KSeF", { it.status }) {
            readJson<AuthStatusResponse>(
                get("/auth/${init.referenceNumber}", bearer = authToken, operation = "Status logowania"),
                "Status logowania",
            )
        }

        return post("/auth/token/redeem", body = null, bearer = authToken, operation = "Odbiór tokenów sesji")
            .let { readJson<RedeemResponse>(it, "Odbiór tokenów sesji") }
            .accessToken.token
    }

    /**
     * Pobiera faktury zakupowe (nabywca) z zakresu dat. Zakres jest dzielony na okna zgodne z limitem KSeF;
     * paczka obcięta przez KSeF jest dobierana kolejnymi eksportami od daty kontynuacji.
     */
    fun downloadInvoices(accessToken: String, from: Instant, to: Instant): List<InvoiceFile> {
        val encryptionCertificate = fetchCertificate(USAGE_SYMMETRIC_KEY_ENCRYPTION)
        val invoices = linkedMapOf<String, InvoiceFile>()
        var windowStart = from
        while (windowStart < to) {
            val windowEnd = minOf(windowStart.plus(EXPORT_WINDOW), to)
            var cursor = windowStart
            while (true) {
                val export = exportPackage(accessToken, cursor, windowEnd, encryptionCertificate)
                export.files.forEach { invoices.putIfAbsent(it.name, it) }
                if (!export.isTruncated) break
                val next = export.continuationDate
                    ?: throw KsefException("Paczka faktur została obcięta, a KSeF nie podał daty kontynuacji")
                if (next <= cursor) {
                    throw KsefException("Nie można pobrać paczki faktur: KSeF obciął paczkę bez postępu w zakresie dat")
                }
                cursor = next
            }
            windowStart = windowEnd
        }
        return invoices.values.toList()
    }

    private fun exportPackage(
        accessToken: String,
        from: Instant,
        to: Instant,
        encryptionCertificate: CertificateWithId,
    ): ExportResult {
        val symmetricKey = crypto.newAes256Key()
        val iv = crypto.newIv()
        val body = mapOf(
            "encryption" to mapOf(
                "encryptedSymmetricKey" to Base64.getEncoder()
                    .encodeToString(crypto.rsaOaepEncrypt(symmetricKey, encryptionCertificate.certificate)),
                "initializationVector" to Base64.getEncoder().encodeToString(iv),
                "publicKeyId" to encryptionCertificate.publicKeyId,
            ),
            "filters" to mapOf(
                "subjectType" to "Subject2",
                "dateRange" to mapOf(
                    "dateType" to "PermanentStorage",
                    "from" to from.toString(),
                    "to" to to.toString(),
                ),
            ),
            "compressionType" to "Zip",
        )
        val reference = post("/invoices/exports", body, bearer = accessToken, operation = "Eksport faktur")
            .let { readJson<ExportResponse>(it, "Eksport faktur") }
            .referenceNumber

        val status = awaitStatus("Eksport faktur", { it.status }) {
            readJson<ExportStatusResponse>(
                get("/invoices/exports/$reference", bearer = accessToken, operation = "Status eksportu"),
                "Status eksportu",
            )
        }
        val pkg = status.`package` ?: throw KsefException("Eksport faktur zakończony bez paczki")

        val encrypted = ByteArrayOutputStream()
        pkg.parts.sortedBy { it.ordinalNumber }.forEach { part ->
            val bytes = download(part.url, part.encryptedPartHash)
            encrypted.write(bytes)
        }
        val archive = crypto.aes256CbcDecrypt(encrypted.toByteArray(), symmetricKey, iv)

        return ExportResult(
            files = crypto.unzip(archive),
            isTruncated = pkg.isTruncated,
            continuationDate = pkg.lastPermanentStorageDate?.let(::parseKsefDate),
        )
    }

    private fun download(url: String, expectedHash: String): ByteArray {
        val response = send(HttpRequest.newBuilder(URI.create(url)).GET().build(), "Pobranie części paczki")
        requireSuccess(response, "Pobranie części paczki")
        val bytes = response.body()
        if (crypto.sha256Base64(bytes) != expectedHash) {
            throw KsefException("Pobrana część paczki ma niezgodny skrót SHA-256")
        }
        return bytes
    }

    private fun fetchCertificate(usage: String): CertificateWithId {
        val certificates = get("/security/public-key-certificates", operation = "Pobranie certyfikatów KSeF")
            .let { readJson<Array<PublicKeyCertificate>>(it, "Pobranie certyfikatów KSeF") }
        val now = Instant.now()
        val certificate = certificates
            .filter { usage in it.usage && parseKsefDate(it.validTo).isAfter(now) }
            .maxByOrNull { parseKsefDate(it.validTo) }
            ?: throw KsefException("KSeF nie zwrócił aktywnego certyfikatu: $usage")
        return CertificateWithId(crypto.parseCertificate(certificate.certificate), certificate.publicKeyId)
    }

    private fun <T> awaitStatus(operation: String, statusOf: (T) -> StatusInfo, fetch: () -> T): T {
        repeat(maxPolls) {
            val result = fetch()
            val status = statusOf(result)
            when (status.code) {
                STATUS_SUCCESS -> return result
                STATUS_IN_PROGRESS -> Thread.sleep(pollInterval.toMillis())
                else -> throw KsefException("$operation: KSeF zwrócił status ${status.code} (${status.description})")
            }
        }
        throw KsefException("$operation: przekroczono czas oczekiwania na odpowiedź KSeF")
    }

    private fun get(path: String, bearer: String? = null, operation: String): HttpResponse<ByteArray> {
        val request = request(path, bearer).GET().build()
        return send(request, operation).also { requireSuccess(it, operation) }
    }

    private fun post(
        path: String,
        body: Any?,
        bearer: String? = null,
        operation: String,
    ): HttpResponse<ByteArray> {
        val builder = request(path, bearer).header("Content-Type", "application/json")
        val payload = if (body == null) "" else mapper.writeValueAsString(body)
        val request = builder.POST(HttpRequest.BodyPublishers.ofString(payload)).build()
        return send(request, operation).also { requireSuccess(it, operation) }
    }

    private fun request(path: String, bearer: String?): HttpRequest.Builder {
        val builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(60))
        return if (bearer != null) builder.header("Authorization", "Bearer $bearer") else builder
    }

    private fun send(request: HttpRequest, operation: String): HttpResponse<ByteArray> =
        try {
            http.send(request, HttpResponse.BodyHandlers.ofByteArray())
        } catch (e: IOException) {
            throw KsefException("$operation: brak połączenia z KSeF (${e.message})", e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw KsefException("$operation: przerwano oczekiwanie na KSeF", e)
        }

    private fun requireSuccess(response: HttpResponse<ByteArray>, operation: String) {
        if (response.statusCode() !in 200..299) {
            throw KsefException("$operation: KSeF zwrócił HTTP ${response.statusCode()}${describeError(response.body())}")
        }
    }

    private fun describeError(body: ByteArray): String =
        try {
            val details = mapper.readTree(body).path("exception").path("exceptionDetailList")
            val description = details.firstOrNull()?.path("exceptionDescription")?.asString()
            if (description.isNullOrBlank()) "" else " ($description)"
        } catch (e: Exception) {
            ""
        }

    private inline fun <reified T> readJson(response: HttpResponse<ByteArray>, operation: String): T =
        try {
            mapper.readValue(response.body(), T::class.java)
        } catch (e: Exception) {
            throw KsefException("$operation: nieoczekiwana odpowiedź KSeF", e)
        }

    private fun parseKsefDate(value: String): Instant =
        try {
            OffsetDateTime.parse(value).toInstant()
        } catch (e: DateTimeParseException) {
            throw KsefException("Nieprawidłowa data w odpowiedzi KSeF: $value", e)
        }

    private data class CertificateWithId(val certificate: X509Certificate, val publicKeyId: String)

    private data class ExportResult(
        val files: List<InvoiceFile>,
        val isTruncated: Boolean,
        val continuationDate: Instant?,
    )

    private data class ChallengeResponse(val challenge: String, val timestampMs: Long)
    private data class AuthInitResponse(val referenceNumber: String, val authenticationToken: TokenValue)
    private data class TokenValue(val token: String)
    private data class StatusInfo(val code: Int, val description: String? = null)
    private data class AuthStatusResponse(val status: StatusInfo)
    private data class RedeemResponse(val accessToken: TokenValue)
    private data class PublicKeyCertificate(
        val certificate: String,
        val publicKeyId: String,
        val usage: List<String>,
        val validTo: String,
    )
    private data class ExportResponse(val referenceNumber: String)
    private data class ExportStatusResponse(val status: StatusInfo, val `package`: ExportPackage?)
    private data class ExportPackage(
        val parts: List<PackagePart>,
        val isTruncated: Boolean = false,
        val lastPermanentStorageDate: String? = null,
    )
    private data class PackagePart(val ordinalNumber: Int, val url: String, val encryptedPartHash: String)

    private companion object {
        const val USAGE_TOKEN_ENCRYPTION = "KsefTokenEncryption"
        const val USAGE_SYMMETRIC_KEY_ENCRYPTION = "SymmetricKeyEncryption"
        const val STATUS_IN_PROGRESS = 100
        const val STATUS_SUCCESS = 200
        val EXPORT_WINDOW: Duration = Duration.ofDays(90)
    }
}
