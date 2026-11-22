package com.turbostevek.ksefimporter.ksef

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.MGF1ParameterSpec
import java.util.Base64
import java.util.zip.ZipInputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

internal object KsefCrypto {

    private val oaepParameters = OAEPParameterSpec(
        "SHA-256",
        "MGF1",
        MGF1ParameterSpec.SHA256,
        PSource.PSpecified.DEFAULT,
    )

    fun parseCertificate(base64Der: String): X509Certificate =
        CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(Base64.getDecoder().decode(base64Der))) as X509Certificate

    fun rsaOaepEncrypt(data: ByteArray, certificate: X509Certificate): ByteArray {
        val cipher = Cipher.getInstance(RSA_OAEP)
        cipher.init(Cipher.ENCRYPT_MODE, certificate.publicKey, oaepParameters)
        return cipher.doFinal(data)
    }

    fun newAes256Key(): ByteArray =
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().encoded

    fun newIv(): ByteArray = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }

    fun aes256CbcDecrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_CBC)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    fun sha256Base64(data: ByteArray): String =
        Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(data))

    fun unzip(archive: ByteArray): List<InvoiceFile> {
        val files = mutableListOf<InvoiceFile>()
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            generateSequence { zip.nextEntry }
                .filter { !it.isDirectory }
                .forEach { entry ->
                    val buffer = ByteArrayOutputStream()
                    zip.copyTo(buffer)
                    files += InvoiceFile(entry.name, buffer.toByteArray())
                }
        }
        return files
    }

    private const val RSA_OAEP = "RSA/ECB/OAEPPadding"
    private const val AES_CBC = "AES/CBC/PKCS5Padding"
    private const val IV_LENGTH = 16
}
