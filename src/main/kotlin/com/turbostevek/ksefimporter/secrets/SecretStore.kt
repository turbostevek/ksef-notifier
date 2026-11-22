package com.turbostevek.ksefimporter.secrets

const val SECRET_SERVICE = "ksef-importer"

/**
 * Read-only access to secrets kept in the operating system's secret store
 * (Windows Credential Manager or libsecret). Values are never written to config files or logs.
 */
interface SecretStore {
    /** Returns the secret value, or `null` when no secret with this name is stored. */
    fun read(name: SecretName): String?
}

enum class SecretName(val account: String) {
    KSEF_TOKEN("ksef-token");

    val qualifiedName: String
        get() = "$SECRET_SERVICE/$account"
}

class SecretStoreException(message: String) : RuntimeException(message)
