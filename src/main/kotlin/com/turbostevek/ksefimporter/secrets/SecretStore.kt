package com.turbostevek.ksefimporter.secrets

const val SECRET_SERVICE = "ksef-importer"

interface SecretStore {
    fun read(name: SecretName): String?
}

enum class SecretName(val account: String) {
    KSEF_TOKEN("ksef-token");

    val qualifiedName: String
        get() = "$SECRET_SERVICE/$account"
}

class SecretStoreException(message: String) : RuntimeException(message)
