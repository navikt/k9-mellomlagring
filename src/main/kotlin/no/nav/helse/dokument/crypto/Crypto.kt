package no.nav.helse.dokument.crypto

import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private const val ALGO = "AES/GCM/NoPadding"

class Crypto(
    passphrase: String,
    private val iv: String
) {
    private val key: SecretKey

    init {
        if (passphrase.isBlank() || iv.isBlank()) {
            throw IllegalArgumentException("Passphrase og iv må settes")
        }
        key = key(passphrase, iv)
    }

    fun encrypt(plainText: String): String {
        try {
            val cipher = Cipher.getInstance(ALGO)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv.toByteArray()))
            return Base64.getUrlEncoder().encodeToString(cipher.doFinal(plainText.toByteArray()))
        } catch (ex: Exception) {
            throw RuntimeException("Error while encrypting text", ex)
        }
    }

    fun decrypt(encrypted: String): String {
        try {
            val cipher = Cipher.getInstance(ALGO)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv.toByteArray()))
            return String(cipher.doFinal(Base64.getUrlDecoder().decode(encrypted)))
        } catch (ex: Exception) {
            throw RuntimeException("Error while decrypting text", ex)
        }
    }

    private fun key(passphrase: String, salt: String): SecretKey {
        try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val passwordChars = passphrase.toCharArray()
            val spec = PBEKeySpec(passwordChars, salt.toByteArray(), 10000, 256)
            val key = factory.generateSecret(spec)
            return SecretKeySpec(key.encoded, "AES")
        } catch (ex: Exception) {
            throw RuntimeException("Error while generating key", ex)
        }

    }
}
