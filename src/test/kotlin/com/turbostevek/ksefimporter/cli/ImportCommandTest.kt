package com.turbostevek.ksefimporter.cli

import com.turbostevek.ksefimporter.ksef.KsefClientFactory
import com.turbostevek.ksefimporter.secrets.SecretName
import com.turbostevek.ksefimporter.secrets.SecretStore
import com.turbostevek.ksefimporter.secrets.SecretStoreException
import com.turbostevek.ksefimporter.validConfigYaml
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

    private val clientFactory = KsefClientFactory()

    private val originalOut = System.out
    private val originalErr = System.err
    private val outBuffer = ByteArrayOutputStream()
    private val errBuffer = ByteArrayOutputStream()

    @BeforeEach
    fun captureOutput() {
        System.setOut(PrintStream(outBuffer, true, Charsets.UTF_8))
        System.setErr(PrintStream(errBuffer, true, Charsets.UTF_8))
    }

    @AfterEach
    fun restoreOutput() {
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    @Test
    fun `import bez tokenu KSeF konczy sie kodem 1`() {
        val config = writeConfig(VALID_YAML)

        val command = ImportCommand(FakeSecretStore(), clientFactory)
        command.run(DefaultApplicationArguments("import", "--config=${config}"))

        assertEquals(1, command.exitCode)
        assertTrue(errBuffer.toString(Charsets.UTF_8).contains("ksef-importer/ksef-token"))
    }

    @Test
    fun `brak wymaganego parametru wypisuje czytelny blad i konczy sie kodem 1`() {
        val config = writeConfig(VALID_YAML.replace("startDate: 2026-01-01\n", ""))

        val command = ImportCommand(FakeSecretStore(), clientFactory)
        command.run(DefaultApplicationArguments("import", "--config=${config}"))

        assertEquals(1, command.exitCode)
        assertTrue(errBuffer.toString(Charsets.UTF_8).contains("startDate"))
    }

    @Test
    fun `brak polecenia import konczy sie kodem 2`() {
        val command = ImportCommand(FakeSecretStore(), clientFactory)
        command.run(DefaultApplicationArguments())

        assertEquals(2, command.exitCode)
    }

    @Test
    fun `check-token z zapisanym tokenem konczy sie kodem 0 bez wypisywania wartosci`() {
        val command = ImportCommand(FakeSecretStore(SecretName.KSEF_TOKEN to SECRET_TOKEN), clientFactory)
        command.run(DefaultApplicationArguments("check-token"))

        assertEquals(0, command.exitCode)
        assertFalse(outBuffer.toString(Charsets.UTF_8).contains(SECRET_TOKEN))
        assertFalse(errBuffer.toString(Charsets.UTF_8).contains(SECRET_TOKEN))
    }

    @Test
    fun `check-token bez tokenu wskazuje nazwe sekretu do ustawienia i konczy sie kodem 1`() {
        val command = ImportCommand(FakeSecretStore(), clientFactory)
        command.run(DefaultApplicationArguments("check-token"))

        assertEquals(1, command.exitCode)
        assertTrue(errBuffer.toString(Charsets.UTF_8).contains("ksef-importer/ksef-token"))
    }

    @Test
    fun `check-token gdy magazyn sekretow zawiedzie konczy sie kodem 1`() {
        val command = ImportCommand(FailingSecretStore(), clientFactory)
        command.run(DefaultApplicationArguments("check-token"))

        assertEquals(1, command.exitCode)
        assertTrue(errBuffer.toString(Charsets.UTF_8).contains("magazyn"))
    }

    @Test
    fun `config podany ze spacja zamiast znaku równości konczy sie kodem 2`() {
        val config = writeConfig(VALID_YAML)

        val command = ImportCommand(FakeSecretStore(), clientFactory)
        command.run(DefaultApplicationArguments("import", "--config", config.toString()))

        assertEquals(2, command.exitCode)
    }

    @Test
    fun `config bez wartosci konczy sie kodem 2`() {
        val command = ImportCommand(FakeSecretStore(), clientFactory)
        command.run(DefaultApplicationArguments("import", "--config"))

        assertEquals(2, command.exitCode)
    }

    @Test
    fun `config z pusta wartoscia po znaku równości konczy sie kodem 2`() {
        val command = ImportCommand(FakeSecretStore(), clientFactory)
        command.run(DefaultApplicationArguments("import", "--config="))

        assertEquals(2, command.exitCode)
    }

    private fun writeConfig(yaml: String): Path =
        dir.resolve("ksef-importer.yml").also { Files.writeString(it, yaml) }

    private class FakeSecretStore(vararg entries: Pair<SecretName, String>) : SecretStore {
        private val values = mapOf(*entries)

        override fun read(name: SecretName): String? = values[name]
    }

    private class FailingSecretStore : SecretStore {
        override fun read(name: SecretName): String? = throw SecretStoreException("Nie można odczytać magazynu sekretów")
    }

    private companion object {
        const val SECRET_TOKEN = "super-tajny-token-123"
        val VALID_YAML = validConfigYaml
    }
}
