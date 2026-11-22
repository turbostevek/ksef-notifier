package com.turbostevek.ksefimporter.secrets

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.ptr.PointerByReference

/**
 * Generic credential target is `service/account`. The password is read as UTF-16LE,
 * the encoding Credential Manager uses when a password is entered in its UI.
 */
class WindowsCredentialSecretStore : SecretStore {

    override fun read(name: SecretName): String? {
        val credentialRef = PointerByReference()
        if (!Advapi32.INSTANCE.CredReadW(WString(name.qualifiedName), CRED_TYPE_GENERIC, 0, credentialRef)) {
            val error = Native.getLastError()
            if (error == ERROR_NOT_FOUND) return null
            throw SecretStoreException("Odczyt sekretu '${name.qualifiedName}' nie powiódł się (kod błędu Windows $error).")
        }
        try {
            val credential = NativeCredential(credentialRef.value)
            val blob = credential.credentialBlob?.getByteArray(0, credential.credentialBlobSize)
                ?: return null
            return String(blob, Charsets.UTF_16LE).ifEmpty { null }
        } finally {
            Advapi32.INSTANCE.CredFree(credentialRef.value)
        }
    }

    private interface Advapi32 : StdCallLibrary {
        fun CredReadW(targetName: WString, type: Int, flags: Int, credential: PointerByReference): Boolean
        fun CredFree(credential: Pointer)

        companion object {
            val INSTANCE: Advapi32 by lazy { Native.load("advapi32", Advapi32::class.java) }
        }
    }

    /** Prefix of the native CREDENTIALW structure; only the fields needed to read the blob are mapped. */
    @Structure.FieldOrder("flags", "type", "targetName", "comment", "lastWrittenLow", "lastWrittenHigh", "credentialBlobSize", "credentialBlob")
    class NativeCredential(address: Pointer) : Structure(address) {
        @JvmField var flags: Int = 0
        @JvmField var type: Int = 0
        @JvmField var targetName: Pointer? = null
        @JvmField var comment: Pointer? = null
        @JvmField var lastWrittenLow: Int = 0
        @JvmField var lastWrittenHigh: Int = 0
        @JvmField var credentialBlobSize: Int = 0
        @JvmField var credentialBlob: Pointer? = null

        init {
            read()
        }
    }

    private companion object {
        const val CRED_TYPE_GENERIC = 1
        const val ERROR_NOT_FOUND = 1168
    }
}
