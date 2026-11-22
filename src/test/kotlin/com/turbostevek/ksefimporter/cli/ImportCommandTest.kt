package com.turbostevek.ksefimporter.cli

import com.turbostevek.ksefimporter.validConfigYaml
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.DefaultApplicationArguments
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path

class ImportCommandTest {

    @TempDir
    lateinit var dir: Path

    private val originalErr = System.err
    private val errBuffer = ByteArrayOutputStream()

    @BeforeEach
    fun captureStderr() {
        System.setErr(PrintStream(errBuffer, true, Charsets.UTF_8))
    }

    @AfterEach
    fun restoreStderr() {
        System.setErr(originalErr)
    }

    @Test
    fun `import z poprawna konfiguracja konczy sie kodem 0`() {
        val config = writeConfig(VALID_YAML)

        val command = ImportCommand()
        command.run(DefaultApplicationArguments("import", "--config=${config}"))

        assertEquals(0, command.exitCode)
        assertEquals("", errBuffer.toString(Charsets.UTF_8))
    }

    @Test
    fun `brak wymaganego parametru wypisuje czytelny blad i konczy sie kodem 1`() {
        val config = writeConfig(VALID_YAML.replace("startDate: 2026-01-01\n", ""))

        val command = ImportCommand()
        command.run(DefaultApplicationArguments("import", "--config=${config}"))

        assertEquals(1, command.exitCode)
        assertTrue(errBuffer.toString(Charsets.UTF_8).contains("startDate"))
    }

    @Test
    fun `brak polecenia import konczy sie kodem 2`() {
        val command = ImportCommand()
        command.run(DefaultApplicationArguments())

        assertEquals(2, command.exitCode)
    }

    @Test
    fun `config podany ze spacja zamiast znaku równości konczy sie kodem 2`() {
        val config = writeConfig(VALID_YAML)

        val command = ImportCommand()
        command.run(DefaultApplicationArguments("import", "--config", config.toString()))

        assertEquals(2, command.exitCode)
    }

    @Test
    fun `config bez wartosci konczy sie kodem 2`() {
        val command = ImportCommand()
        command.run(DefaultApplicationArguments("import", "--config"))

        assertEquals(2, command.exitCode)
    }

    @Test
    fun `config z pusta wartoscia po znaku równości konczy sie kodem 2`() {
        val command = ImportCommand()
        command.run(DefaultApplicationArguments("import", "--config="))

        assertEquals(2, command.exitCode)
    }

    private fun writeConfig(yaml: String): Path =
        dir.resolve("ksef-importer.yml").also { Files.writeString(it, yaml) }

    private companion object {
        val VALID_YAML = validConfigYaml
    }
}
