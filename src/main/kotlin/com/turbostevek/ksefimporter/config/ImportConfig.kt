package com.turbostevek.ksefimporter.config

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

@JvmInline
value class Nip(val value: String)

data class ImportConfig(
    val importTime: LocalTime,
    val startDate: LocalDate,
    val nip: Nip,
    val recipientEmail: String,
    val ksefEnvironment: KsefEnvironment,
    val smtp: SmtpSettings,
)

data class SmtpSettings(
    val host: String,
    val port: Int,
    val username: String?,
    val from: String,
)

enum class KsefEnvironment(val value: String, val apiBaseUrl: String) {
    TEST("test", "https://api-test.ksef.mf.gov.pl/v2"),
    PROD("prod", "https://api.ksef.mf.gov.pl/v2");

    companion object {
        fun fromValue(value: String): KsefEnvironment? =
            entries.find { it.value.equals(value, ignoreCase = true) }
    }
}

class ConfigException(message: String) : RuntimeException(message)

class ImportConfigLoader {

    private val parser = ConfigFileParser()

    fun load(path: Path): ImportConfig {
        if (!Files.isRegularFile(path)) {
            throw ConfigException("Nie znaleziono pliku konfiguracyjnego: $path")
        }
        val text = try {
            Files.readString(path)
        } catch (e: IOException) {
            throw ConfigException("Nie można odczytać pliku konfiguracyjnego $path: ${e.message}")
        }
        return parse(text)
    }

    fun parse(yaml: String): ImportConfig {
        val file = parser.parse(yaml)
        val smtp = file.smtp
        val missing = mutableListOf<String>()

        fun required(name: String, value: String?): String =
            value?.takeIf { it.isNotBlank() } ?: run {
                missing += name
                ""
            }

        val importTime = required("importTime", file.importTime)
        val startDate = required("startDate", file.startDate)
        val nip = required("nip", file.nip)
        val recipientEmail = required("recipientEmail", file.recipientEmail)
        val host = required("smtp.host", smtp?.host)
        val from = required("smtp.from", smtp?.from)
        if (missing.isNotEmpty()) {
            throw ConfigException("Brak wymaganych parametrów w konfiguracji: ${missing.joinToString(", ")}")
        }
        if (!nip.matches(NIP_PATTERN)) {
            throw ConfigException("Nieprawidłowa wartość parametru nip: '$nip' (wymagane 10 cyfr)")
        }

        val environment = file.ksef?.environment?.let {
            KsefEnvironment.fromValue(it)
                ?: throw ConfigException("Nieznane środowisko KSeF: '$it' (dozwolone: test, prod)")
        } ?: KsefEnvironment.TEST

        return ImportConfig(
            importTime = parseOrFail("importTime", importTime) { LocalTime.parse(it) },
            startDate = parseOrFail("startDate", startDate) { LocalDate.parse(it) },
            nip = Nip(nip),
            recipientEmail = recipientEmail,
            ksefEnvironment = environment,
            smtp = SmtpSettings(
                host = host,
                port = smtp?.port ?: DEFAULT_SMTP_PORT,
                username = smtp?.username,
                from = from,
            ),
        )
    }

    private fun <T> parseOrFail(name: String, raw: String, parse: (String) -> T): T =
        try {
            parse(raw)
        } catch (e: DateTimeParseException) {
            throw ConfigException("Nieprawidłowa wartość parametru $name: '$raw'")
        }

    private companion object {
        const val DEFAULT_SMTP_PORT = 587
        val NIP_PATTERN = Regex("\\d{10}")
    }
}
