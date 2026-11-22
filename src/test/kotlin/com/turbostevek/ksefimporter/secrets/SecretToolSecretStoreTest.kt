package com.turbostevek.ksefimporter.secrets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

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
