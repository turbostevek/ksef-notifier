package com.turbostevek.ksefimporter.secrets

import java.io.IOException

class SecretToolSecretStore(private val command: String = "secret-tool") : SecretStore {

    override fun read(name: SecretName): String? {
        val process = try {
            ProcessBuilder(command, "lookup", "service", SECRET_SERVICE, "account", name.account).start()
        } catch (e: IOException) {
            throw SecretStoreException("Nie można uruchomić '$command'. Zainstaluj pakiet libsecret-tools.")
        }
        process.outputStream.close()
        val value = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val errors = process.errorStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.trim()
        val exitCode = process.waitFor()

        return when {
            exitCode == 0 && value.isNotEmpty() -> value.trimEnd('\n', '\r')
            exitCode == 0 -> null
            // secret-tool exits with 1 and writes nothing to stderr when no item matches;
            // any other failure (e.g. no D-Bus session) explains itself on stderr
            exitCode == SECRET_NOT_FOUND_EXIT_CODE && errors.isEmpty() -> null
            else -> throw SecretStoreException(
                "Nie można odczytać sekretu '${name.qualifiedName}': ${errors.ifEmpty { "kod $exitCode" }}",
            )
        }
    }

    private companion object {
        const val SECRET_NOT_FOUND_EXIT_CODE = 1
    }
}
