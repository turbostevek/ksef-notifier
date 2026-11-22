package com.turbostevek.ksefimporter.config

import com.turbostevek.ksefimporter.validConfigYaml
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Paths
import java.time.LocalDate
import java.time.LocalTime

class ImportConfigLoaderTest {

    private val loader = ImportConfigLoader()
    private val minimalValidYaml = validConfigYaml

    @Test
    fun `wczytuje wszystkie parametry z pliku konfiguracyjnego`() {
        val config = loader.parse(
            """
            importTime: "07:30"
            startDate: 2026-01-01
            recipientEmail: zona@example.com
            ksef:
              environment: prod
            smtp:
              host: smtp.example.com
              port: 465
              username: ksef@example.com
              from: ksef@example.com
            """.trimIndent()
        )

        assertEquals(LocalTime.of(7, 30), config.importTime)
        assertEquals(LocalDate.of(2026, 1, 1), config.startDate)
        assertEquals("zona@example.com", config.recipientEmail)
        assertEquals(KsefEnvironment.PROD, config.ksefEnvironment)
        assertEquals("smtp.example.com", config.smtp.host)
        assertEquals(465, config.smtp.port)
        assertEquals("ksef@example.com", config.smtp.username)
        assertEquals("ksef@example.com", config.smtp.from)
    }

    @Test
    fun `bez sekcji ksef srodowiskiem jest test`() {
        val config = loader.parse(minimalValidYaml)

        assertEquals(KsefEnvironment.TEST, config.ksefEnvironment)
    }

    @Test
    fun `brak wymaganego parametru zglasza jego nazwe`() {
        val yaml = minimalValidYaml.replace("recipientEmail: zona@example.com\n", "")

        val error = assertThrows<ConfigException> { loader.parse(yaml) }

        assertTrue(error.message!!.contains("recipientEmail"), error.message)
    }

    @Test
    fun `pusta wartosc wymaganego parametru jest traktowana jak brak`() {
        val yaml = minimalValidYaml.replace("recipientEmail: zona@example.com", "recipientEmail: \"\"")

        val error = assertThrows<ConfigException> { loader.parse(yaml) }

        assertTrue(error.message!!.contains("recipientEmail"), error.message)
    }

    @Test
    fun `plik z haslem SMTP jest odrzucany`() {
        val yaml = minimalValidYaml + "  password: tajne\n"

        val error = assertThrows<ConfigException> { loader.parse(yaml) }

        assertTrue(error.message!!.contains("password"), error.message)
    }

    @Test
    fun `nieprawidlowa godzina importu zglasza nazwe parametru`() {
        val yaml = minimalValidYaml.replace("\"07:30\"", "\"25:99\"")

        val error = assertThrows<ConfigException> { loader.parse(yaml) }

        assertTrue(error.message!!.contains("importTime"), error.message)
    }

    @Test
    fun `brak pliku konfiguracyjnego daje czytelny blad`() {
        val path = Paths.get("nie-ma-takiego-pliku.yml")

        val error = assertThrows<ConfigException> { loader.load(path) }

        assertTrue(error.message!!.contains("nie-ma-takiego-pliku.yml"), error.message)
    }
}
