package no.nav.helse

import com.google.cloud.storage.StorageOptions
import io.ktor.config.*
import io.ktor.util.*
import no.nav.helse.dokument.eier.HentEierFra
import no.nav.helse.dokument.storage.GcpStorageBucket
import no.nav.helse.dokument.storage.Storage
import no.nav.helse.dusseldorf.ktor.auth.*
import no.nav.helse.dusseldorf.ktor.core.getOptionalList
import no.nav.helse.dusseldorf.ktor.core.getOptionalString
import no.nav.helse.dusseldorf.ktor.core.getRequiredString
import java.net.URI

@KtorExperimentalAPI
internal data class Configuration(private val config: ApplicationConfig) {

    private companion object {
        private const val CRYPTO_PASSPHRASE_PREFIX = "CRYPTO_PASSPHRASE_"

        private const val LOGIN_SERVICE_V1_ALIAS = "login-service-v1"
    }

    private val issuers = config.issuers().withAdditionalClaimRules(
        mapOf(
            LOGIN_SERVICE_V1_ALIAS to setOf(EnforceEqualsOrContains("acr", "Level4"))
        )
    )

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
    private fun isLoginServiceV1Configured() = issuers.filterKeys { LOGIN_SERVICE_V1_ALIAS == it.alias() }.isNotEmpty()
    internal fun issuers(): Map<Issuer, Set<ClaimRule>> {
        if (issuers.isEmpty()) throw IllegalStateException("Må konfigureres minst en issuer.")
        return issuers
    }

    internal fun hentEierFra() =
        if (isLoginServiceV1Configured()) HentEierFra.ACCESS_TOKEN_SUB_CLAIM else HentEierFra.QUERY_PARAMETER_EIER

    val gcpBucket = config.getOptionalString(key = "nav.storage.gcp_bucket.bucket", secret = false)
    val gcsClientServiceEndpoint = config.getOptionalString(key = "nav.storage.gcp_bucket.service_endpoint", secret = false)

    val gcsConfigured = gcpBucket != null

    internal fun getStorageConfigured(): Storage {

        when {
            gcsConfigured -> {
                return GcpStorageBucket(
                    gcpStorage = getGcpStorageConfigured(),
                    bucketName = gcpBucket!!
                )
            }
            else -> {
                throw IllegalStateException("Ingen storage er konfigurert. Konfigurer enten S3 eller GCS bucket.")
            }
        }
    }

    fun getGcpStorageConfigured(): com.google.cloud.storage.Storage {
        return when {
            gcsClientServiceEndpoint.isNullOrBlank() -> StorageOptions.getDefaultInstance().service
            else -> StorageOptions.newBuilder()
                .setHost(gcsClientServiceEndpoint)
                .build()
                .service
        }
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
