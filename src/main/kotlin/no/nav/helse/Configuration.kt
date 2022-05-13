package no.nav.helse

import com.google.cloud.storage.StorageOptions
import io.ktor.config.*
import io.ktor.util.*
import no.nav.helse.dokument.storage.GcpStorageBucket
import no.nav.helse.dokument.storage.InMemoryStorage
import no.nav.helse.dokument.storage.Storage
import no.nav.helse.dusseldorf.ktor.auth.*
import no.nav.helse.dusseldorf.ktor.core.getOptionalList
import no.nav.helse.dusseldorf.ktor.core.getOptionalString
import no.nav.helse.dusseldorf.ktor.core.getRequiredString
import java.net.URI

internal data class Configuration(private val config: ApplicationConfig) {

    private companion object {
        private const val CRYPTO_PASSPHRASE_PREFIX = "CRYPTO_PASSPHRASE_"
    }

    // Crypto
    private fun getCryptoPasshrase(key: String): String {
        val configValue = config.getOptionalString(key = key, secret = true)
        if (configValue != null) return configValue
        return System.getenv(key) ?: throw IllegalStateException("Environment Variable $key må være satt")
    }

    internal fun getEncryptionPassphrase(): Pair<Int, String> {
        val identifier = config.getRequiredString("nav.crypto.passphrase.encryption_identifier", secret = false).toInt()
        val passphrase = getCryptoPasshrase("$CRYPTO_PASSPHRASE_PREFIX$identifier")
        return Pair(identifier, passphrase)
    }

    fun getDecryptionPassphrases(): Map<Int, String> {
        val identifiers = config.getOptionalList( // Kan være kun den vi krypterer med
            key = "nav.crypto.passphrase.decryption_identifiers",
            builder = { value -> value.toInt() },
            secret = false
        )

        val decryptionPassphrases = mutableMapOf<Int, String>()
        identifiers.forEach { decryptionPassphrases[it] = getCryptoPasshrase("$CRYPTO_PASSPHRASE_PREFIX$it") }
        val encryptionPassphrase = getEncryptionPassphrase()
        decryptionPassphrases[encryptionPassphrase.first] =
            encryptionPassphrase.second // Forsikre oss om at nåværende krypterings-ID alltid er en av decrypterings-ID'ene
        return decryptionPassphrases.toMap()
    }

    // Auth

    val gcpBucket = config.getOptionalString(key = "nav.storage.gcp_bucket.bucket", secret = false)
    val localOrTest = when (config.getOptionalString("nav.local_or_test", secret = false)) {
        "false" -> false
        "true" -> true
        else -> false
    }

    val gcsConfigured = gcpBucket != null

    internal fun getStorageConfigured(): Storage {

        when {
            localOrTest -> {
                return InMemoryStorage()
            }
            gcsConfigured -> {
                return GcpStorageBucket(
                    gcpStorage = getGcpStorageConfigured(),
                    bucketName = gcpBucket!!
                )
            }
            else -> {
                throw IllegalStateException("Ingen storage er konfigurert. Konfigurer enten InMemoryStorage eller GCS bucket.")
            }
        }
    }

    fun getGcpStorageConfigured(): com.google.cloud.storage.Storage {
        return StorageOptions.getDefaultInstance().service
    }

    internal fun getS3ExpirationInDays(): Int? =
        config.getOptionalString("nav.storage.s3.expiration_in_days", secret = false)?.toInt()

    internal fun støtterExpirationFraRequest() = getS3ExpirationInDays() == null

    // Virus Scan
    internal fun enableVirusScan(): Boolean =
        config.getRequiredString("nav.virus_scan.enabled", false).equals("true", true)

    internal fun getVirusScanUrl() = URI(config.getRequiredString("nav.virus_scan.url", secret = false))

    // URL's
    internal fun getBaseUrl(): String = config.getRequiredString("nav.base_url", secret = false)
}
