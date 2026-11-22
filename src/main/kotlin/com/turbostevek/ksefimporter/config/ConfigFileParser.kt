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
    val recipientEmail: String? = null,
    val ksef: KsefFile? = null,
    val smtp: SmtpFile? = null,
)

internal data class KsefFile(
    val environment: String? = null,
)

internal data class SmtpFile(
    val host: String? = null,
    val port: Int? = null,
    val username: String? = null,
    val from: String? = null,
)
