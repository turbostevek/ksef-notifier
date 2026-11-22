package com.turbostevek.ksefimporter.config

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

data class ImportConfig(
    val importTime: LocalTime,
    val startDate: LocalDate,
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

enum class KsefEnvironment(val value: String) {
    TEST("test"),
    PROD("prod");

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

        val missing = buildList {
            if (file.importTime.isNullOrBlank()) add("importTime")
            if (file.startDate.isNullOrBlank()) add("startDate")
            if (file.recipientEmail.isNullOrBlank()) add("recipientEmail")
            if (smtp?.host.isNullOrBlank()) add("smtp.host")
            if (smtp?.from.isNullOrBlank()) add("smtp.from")
        }
        if (missing.isNotEmpty()) {
            throw ConfigException("Brak wymaganych parametrów w konfiguracji: ${missing.joinToString(", ")}")
        }

        val environment = file.ksef?.environment?.let {
            KsefEnvironment.fromValue(it)
                ?: throw ConfigException("Nieznane środowisko KSeF: '$it' (dozwolone: test, prod)")
        } ?: KsefEnvironment.TEST

        return ImportConfig(
            importTime = parseOrFail("importTime", file.importTime!!) { LocalTime.parse(it) },
            startDate = parseOrFail("startDate", file.startDate!!) { LocalDate.parse(it) },
            recipientEmail = file.recipientEmail!!,
            ksefEnvironment = environment,
            smtp = SmtpSettings(
                host = smtp!!.host!!,
                port = smtp.port ?: DEFAULT_SMTP_PORT,
                username = smtp.username,
                from = smtp.from!!,
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
    }
}
