package com.turbostevek.ksefimporter.secrets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

@EnabledOnOs(OS.LINUX)
class SecretToolSecretStoreTest {

    @TempDir
    lateinit var dir: Path

    @Test
    fun `zwraca wartosc sekretu zapisanego pod nazwa uslugi i konta`() {
        val store = SecretToolSecretStore(fakeSecretTool(dir, """
            if [ "$*" = "lookup service ksef-importer account ksef-token" ]; then printf '%s' 'token-z-keyring'; exit 0; fi
            exit 1
        """.trimIndent()))

        assertEquals("token-z-keyring", store.read(SecretName.KSEF_TOKEN))
    }

    @Test
    fun `brak sekretu daje null`() {
        val store = SecretToolSecretStore(fakeSecretTool(dir, "exit 1"))

        assertNull(store.read(SecretName.KSEF_TOKEN))
    }

    @Test
    fun `pusta wartosc sekretu traktowana jak brak sekretu`() {
        val store = SecretToolSecretStore(fakeSecretTool(dir, "printf '\\n'; exit 0"))

        assertNull(store.read(SecretName.KSEF_TOKEN))
    }

    @Test
    fun `blad secret-tool z komunikatem nie jest traktowany jak brak sekretu`() {
        val store = SecretToolSecretStore(fakeSecretTool(dir, """
            echo 'secret-tool: Nie można połączyć z D-Bus' >&2
            exit 1
        """.trimIndent()))

        val error = assertThrows<SecretStoreException> { store.read(SecretName.KSEF_TOKEN) }

        assertTrue(error.message!!.contains("Nie można połączyć z D-Bus"))
    }

    @Test
    fun `brak programu secret-tool zglasza blad magazynu`() {
        val store = SecretToolSecretStore(dir.resolve("nie-ma-takiego-programu").toString())

        assertThrows<SecretStoreException> { store.read(SecretName.KSEF_TOKEN) }
    }

    private fun fakeSecretTool(dir: Path, body: String): String {
        val script = dir.resolve("secret-tool")
        Files.writeString(script, "#!/bin/sh\n$body\n")
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"))
        return script.toString()
    }
}
