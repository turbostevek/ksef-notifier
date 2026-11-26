package com.turbostevek.ksefimporter.config

import com.turbostevek.ksefimporter.validConfigYaml
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
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
            nip: "9261051389"
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
        assertEquals("9261051389", config.nip.value)
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
    fun `brakujace godzina importu i port SMTP biora wartosci z domyslnej konfiguracji`() {
        val config = loader.parse(
            """
            nip: "9261051389"
            startDate: 2026-01-01
            recipientEmail: zona@example.com
            smtp:
              host: smtp.example.com
              from: ksef@example.com
            """.trimIndent()
        )

        assertEquals(LocalTime.of(7, 30), config.importTime)
        assertEquals(587, config.smtp.port)
    }

    @Test
    fun `wartosc z profilu nadpisuje wartosc domyslna`() {
        val yaml = minimalValidYaml.replace("\"07:30\"", "\"06:00\"")

        val config = loader.parse(yaml)

        assertEquals(LocalTime.of(6, 0), config.importTime)
    }

    @Test
    fun `nip o innej dlugosci niz 10 cyfr jest odrzucany`() {
        val yaml = minimalValidYaml.replace("9261051389", "926105138")

        val error = assertThrows<ConfigException> { loader.parse(yaml) }

        assertTrue(error.message!!.contains("nip"), error.message)
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

    @Test
    fun `wzgledna sciezka caCertificate jest liczona wzgledem katalogu pliku konfiguracyjnego`(@TempDir dir: Path) {
        Files.writeString(dir.resolve("norton-root.cer"), "")
        val profile = dir.resolve("profil.yaml")
        Files.writeString(profile, minimalValidYaml + "\n  caCertificate: norton-root.cer\n")

        val config = loader.load(profile)

        assertEquals(dir.resolve("norton-root.cer"), config.smtp.caCertificate)
    }

    @Test
    fun `nieistniejacy caCertificate zglasza jego nazwe`(@TempDir dir: Path) {
        val profile = dir.resolve("profil.yaml")
        Files.writeString(profile, minimalValidYaml + "\n  caCertificate: brak.cer\n")

        val error = assertThrows<ConfigException> { loader.load(profile) }

        assertTrue(error.message!!.contains("brak.cer"), error.message)
    }
}
