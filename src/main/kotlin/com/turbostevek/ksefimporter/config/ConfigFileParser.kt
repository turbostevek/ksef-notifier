package com.turbostevek.ksefimporter.config

import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.module.kotlin.kotlinModule

/**
 * Maps the YAML file to nullable fields so that missing parameters can be reported together.
 * Unknown keys (e.g. `smtp.password`) are rejected, so secrets cannot live in the file.
 */
internal class ConfigFileParser(
    private val mapper: ObjectMapper = YAMLMapper.builder()
        .addModule(kotlinModule())
        .build(),
) {

    fun parse(yaml: String): ConfigFile =
        try {
            mapper.readValue(yaml, ConfigFile::class.java) ?: ConfigFile()
        } catch (e: JacksonException) {
            throw ConfigException("Nieprawidłowy plik konfiguracyjny: ${e.originalMessage}")
        }
}

internal data class ConfigFile(
    val importTime: String? = null,
    val startDate: String? = null,
    val nip: String? = null,
    val recipientEmail: String? = null,
    val ksef: KsefFile? = null,
    val smtp: SmtpFile? = null,
) {

    /** Values set in this file win; missing ones are taken from [defaults], nested sections field by field. */
    fun withDefaults(defaults: ConfigFile): ConfigFile = ConfigFile(
        importTime = importTime ?: defaults.importTime,
        startDate = startDate ?: defaults.startDate,
        nip = nip ?: defaults.nip,
        recipientEmail = recipientEmail ?: defaults.recipientEmail,
        ksef = KsefFile(environment = ksef?.environment ?: defaults.ksef?.environment),
        smtp = SmtpFile(
            host = smtp?.host ?: defaults.smtp?.host,
            port = smtp?.port ?: defaults.smtp?.port,
            username = smtp?.username ?: defaults.smtp?.username,
            from = smtp?.from ?: defaults.smtp?.from,
        ),
    )
}

internal data class KsefFile(
    val environment: String? = null,
)

internal data class SmtpFile(
    val host: String? = null,
    val port: Int? = null,
    val username: String? = null,
    val from: String? = null,
)
