package com.turbostevek.ksefimporter.ksef

import com.turbostevek.ksefimporter.config.Nip
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.GeneralSecurityException
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
 * KSeF API v2 client: logs in with a KSeF token and downloads purchase invoices (buyer = subject 2).
 * All data is processed in memory; invoice files are never written to disk.
 */
class KsefClient(
    apiBaseUrl: String,
    private val pollInterval: Duration = Duration.ofSeconds(1),
) {
    private val baseUrl = apiBaseUrl.trimEnd('/')
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build()
    private val mapper = JsonMapper.builder()
        .addModule(kotlinModule())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()

    /** Logs in with the KSeF token for the given NIP context and returns the session access token. */
    fun login(token: KsefToken, nip: Nip): AccessToken {
        val tokenCertificate = fetchCertificate(USAGE_TOKEN_ENCRYPTION)
        val challenge = post("/auth/challenge", body = null, operation = OperationDescription.CHALLENGE)
            .let { readJson<ChallengeResponse>(it, OperationDescription.CHALLENGE) }
        val encryptedToken = cryptoStep(OperationDescription.LOGIN) {
            KsefCrypto.rsaOaepEncrypt(
                "${token.value}|${challenge.timestampMs}".toByteArray(Charsets.UTF_8),
                tokenCertificate.certificate,
            )
        }
        val authInit = post(
            "/auth/ksef-token",
            body = mapOf(
                "challenge" to challenge.challenge,
                "contextIdentifier" to mapOf("type" to "Nip", "value" to nip.value),
                "encryptedToken" to Base64.getEncoder().encodeToString(encryptedToken),
                "publicKeyId" to tokenCertificate.publicKeyId,
            ),
            operation = OperationDescription.LOGIN,
        ).let { readJson<AuthInitResponse>(it, OperationDescription.LOGIN) }

        val authToken = authInit.authenticationToken.token
        awaitStatus(OperationDescription.LOGIN, { it.status }) { operation ->
            readJson<AuthStatusResponse>(
                get("/auth/${authInit.referenceNumber}", bearer = authToken, operation = operation),
                operation,
            )
        }

        return post("/auth/token/redeem", body = null, bearer = authToken, operation = OperationDescription.REDEEM)
            .let { readJson<RedeemResponse>(it, OperationDescription.REDEEM) }
            .accessToken.token
            .let(::AccessToken)
    }

    /**
     * Downloads purchase invoices (buyer) for the date range. The range is split into windows within the KSeF
     * limit; a package truncated by KSeF is continued with further exports from the continuation date.
     * Only invoice XML files are returned; package metadata (e.g. `_metadata.json`) is skipped.
     */
    fun downloadInvoices(accessToken: AccessToken, from: Instant, to: Instant): List<InvoiceFile> {
        val encryptionCertificate = fetchCertificate(USAGE_SYMMETRIC_KEY_ENCRYPTION)
        val invoices = linkedMapOf<String, InvoiceFile>()
        var windowStart = from
        while (windowStart < to) {
            val windowEnd = minOf(windowStart.plus(EXPORT_WINDOW), to)
            var cursor = windowStart
            while (true) {
                val export = exportPackage(accessToken, cursor, windowEnd, encryptionCertificate)
                export.files.filter { it.name.endsWith(".xml", ignoreCase = true) }.forEach { invoices.putIfAbsent(it.name, it) }
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
        accessToken: AccessToken,
        from: Instant,
        to: Instant,
        encryptionCertificate: CertificateWithId,
    ): ExportResult {
        val symmetricKey = KsefCrypto.newAes256Key()
        val iv = KsefCrypto.newIv()
        val body = mapOf(
            "encryption" to mapOf(
                "encryptedSymmetricKey" to Base64.getEncoder()
                    .encodeToString(
                        cryptoStep(OperationDescription.EXPORT) {
                            KsefCrypto.rsaOaepEncrypt(symmetricKey, encryptionCertificate.certificate)
                        },
                    ),
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
        val reference = post("/invoices/exports", body, bearer = accessToken.value, operation = OperationDescription.EXPORT)
            .let { readJson<ExportResponse>(it, OperationDescription.EXPORT) }
            .referenceNumber

        val status = awaitStatus(OperationDescription.EXPORT, { it.status }) { operation ->
            readJson<ExportStatusResponse>(
                get("/invoices/exports/$reference", bearer = accessToken.value, operation = operation),
                operation,
            )
        }
        val pkg = status.`package` ?: throw ksefError(OperationDescription.EXPORT, "zakończony bez paczki")

        val encrypted = ByteArrayOutputStream()
        pkg.parts.sortedBy { it.ordinalNumber }.forEach { part ->
            val bytes = download(part.url, part.encryptedPartHash)
            encrypted.write(bytes)
        }
        val archive = cryptoStep(OperationDescription.DECRYPT_PACKAGE) {
            KsefCrypto.aes256CbcDecrypt(encrypted.toByteArray(), symmetricKey, iv)
        }

        return ExportResult(
            files = cryptoStep(OperationDescription.UNZIP_PACKAGE) { KsefCrypto.unzip(archive) },
            isTruncated = pkg.isTruncated,
            continuationDate = pkg.lastPermanentStorageDate?.let(::parseKsefDate),
        )
    }

    private fun download(url: String, expectedHash: String): ByteArray {
        val request = HttpRequest.newBuilder(URI.create(url)).GET().timeout(REQUEST_TIMEOUT).build()
        val response = fetch(request, OperationDescription.PART_DOWNLOAD)
        val bytes = response.body()
        if (KsefCrypto.sha256Base64(bytes) != expectedHash) {
            throw KsefException("Pobrana część paczki ma niezgodny skrót SHA-256")
        }
        return bytes
    }

    private fun fetchCertificate(usage: String): CertificateWithId {
        val certificates = get("/security/public-key-certificates", operation = OperationDescription.CERTIFICATES)
            .let { readJson<Array<PublicKeyCertificate>>(it, OperationDescription.CERTIFICATES) }
        val now = Instant.now()
        val certificate = certificates
            .filter { usage in it.usage && parseKsefDate(it.validTo).isAfter(now) }
            .maxByOrNull { parseKsefDate(it.validTo) }
            ?: throw KsefException("KSeF nie zwrócił aktywnego certyfikatu: $usage")
        val x509 = cryptoStep(OperationDescription.CERTIFICATES) { KsefCrypto.parseCertificate(certificate.certificate) }
        return CertificateWithId(x509, certificate.publicKeyId)
    }

    private fun <T> awaitStatus(operation: OperationDescription, statusOf: (T) -> StatusInfo, poll: (OperationDescription) -> T): T {
        repeat(MAX_POLLS) {
            val result = poll(operation)
            val status = statusOf(result)
            when (status.code) {
                STATUS_SUCCESS -> return result
                STATUS_IN_PROGRESS -> sleepBetweenPolls(operation)
                else -> throw ksefError(operation, "KSeF zwrócił status ${status.code} (${status.description})")
            }
        }
        throw ksefError(operation, "przekroczono czas oczekiwania na odpowiedź KSeF")
    }

    private fun sleepBetweenPolls(operation: OperationDescription) {
        try {
            Thread.sleep(pollInterval.toMillis())
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ksefError(operation, "przerwano oczekiwanie na KSeF", e)
        }
    }

    private fun get(path: String, bearer: String? = null, operation: OperationDescription): HttpResponse<ByteArray> {
        return fetch(request(path, bearer).GET().build(), operation)
    }

    private fun post(
        path: String,
        body: Any?,
        bearer: String? = null,
        operation: OperationDescription,
    ): HttpResponse<ByteArray> {
        val builder = request(path, bearer).header("Content-Type", "application/json")
        val payload = if (body == null) "" else mapper.writeValueAsString(body)
        return fetch(builder.POST(HttpRequest.BodyPublishers.ofString(payload)).build(), operation)
    }

    private fun request(path: String, bearer: String?): HttpRequest.Builder {
        val builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .header("Accept", "application/json")
            .timeout(REQUEST_TIMEOUT)
        return if (bearer != null) builder.header("Authorization", "Bearer $bearer") else builder
    }

    private fun fetch(request: HttpRequest, operation: OperationDescription): HttpResponse<ByteArray> =
        send(request, operation).also { requireSuccess(it, operation) }

    private fun send(request: HttpRequest, operation: OperationDescription): HttpResponse<ByteArray> =
        try {
            http.send(request, HttpResponse.BodyHandlers.ofByteArray())
        } catch (e: IOException) {
            throw ksefError(operation, "brak połączenia z KSeF (${e.message})", e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ksefError(operation, "przerwano oczekiwanie na KSeF", e)
        }

    private fun requireSuccess(response: HttpResponse<ByteArray>, operation: OperationDescription) {
        if (response.statusCode() !in 200..299) {
            throw ksefError(operation, "KSeF zwrócił HTTP ${response.statusCode()}${describeError(response.body())}")
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

    private inline fun <reified T> readJson(response: HttpResponse<ByteArray>, operation: OperationDescription): T =
        try {
            mapper.readValue(response.body(), T::class.java)
        } catch (e: Exception) {
            throw ksefError(operation, "nieoczekiwana odpowiedź KSeF", e)
        }

    private inline fun <T> cryptoStep(operation: OperationDescription, block: () -> T): T =
        try {
            block()
        } catch (e: GeneralSecurityException) {
            throw ksefError(operation, "błąd kryptograficzny (${e.message})", e)
        } catch (e: IOException) {
            throw ksefError(operation, "nieprawidłowe dane paczki (${e.message})", e)
        } catch (e: IllegalArgumentException) {
            throw ksefError(operation, "nieprawidłowe dane KSeF (${e.message})", e)
        }

    private fun ksefError(operation: OperationDescription, detail: String, cause: Throwable? = null): KsefException =
        KsefException("${operation.label}: $detail", cause)

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
        const val MAX_POLLS = 60
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(15)
        val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(60)
        val EXPORT_WINDOW: Duration = Duration.ofDays(90)
    }
}
