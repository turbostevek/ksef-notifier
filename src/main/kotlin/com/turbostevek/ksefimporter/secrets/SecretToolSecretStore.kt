package com.turbostevek.ksefimporter.secrets

import java.io.IOException

/** Linux implementation backed by libsecret through the `secret-tool` command. */
class SecretToolSecretStore(private val command: String = "secret-tool") : SecretStore {

    override fun read(name: SecretName): String? {
        val process = try {
            ProcessBuilder(command, "lookup", "service", SECRET_SERVICE, "account", name.account)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        } catch (e: IOException) {
            throw SecretStoreException("Nie można uruchomić '$command'. Zainstaluj pakiet libsecret-tools.")
        }
        process.outputStream.close()
        val value = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val exitCode = process.waitFor()

        return when {
            exitCode == 0 && value.isNotEmpty() -> value.trimEnd('\n', '\r')
            // secret-tool exits with 1 when no matching item exists
            exitCode == 0 || exitCode == SECRET_NOT_FOUND_EXIT_CODE -> null
            else -> throw SecretStoreException("Odczyt sekretu '${name.qualifiedName}' nie powiódł się (kod $exitCode).")
        }
    }

    private companion object {
        const val SECRET_NOT_FOUND_EXIT_CODE = 1
    }
}
