package no.nav.helse.dokument.storage

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Bucket
import com.google.cloud.storage.BucketInfo.LifecycleRule
import com.google.cloud.storage.StorageException
import com.google.common.collect.ImmutableList
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime


class GcpStorageBucket(
    private val gcpStorage: com.google.cloud.storage.Storage,
    private val bucketName: String
) : Storage {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(GcpStorageBucket::class.java)
    }

    init {
        ensureBucketExists()
        enableLifecycleManagement()
    }

    fun enableLifecycleManagement() {
        val bucket: Bucket = gcpStorage.get(bucketName)

        // https://googleapis.dev/java/google-cloud-clients/latest/com/google/cloud/storage/BucketInfo.LifecycleRule.html
        bucket
            .toBuilder()
            .setLifecycleRules(
                ImmutableList.of(
                    LifecycleRule(
                        LifecycleRule.LifecycleAction.newDeleteAction(),
                        LifecycleRule.LifecycleCondition.newBuilder().setDaysSinceCustomTime(1).build()
                    )
                )
            )
            .build()
            .update()
        logger.info("Lifecycle management aktivert og konfigurert for bucket $bucketName")
    }

    override fun hent(key: StorageKey): StorageValue? {
        return try {
            val blob = gcpStorage.get(BlobId.of(bucketName, key.value)) ?: return null
            val outputStream = ByteArrayOutputStream()
            blob.downloadTo(outputStream)

            StorageValue(String(outputStream.toByteArray()))
        } catch (ex: StorageException) {
            logger.error("Henting av dokument med id ${key.value} feilet.", ex)
            null
        }
    }

    override fun slett(storageKey: StorageKey): Boolean {
        val value = hent(storageKey)
        return if (value == null) false else {
            return try {
                gcpStorage.delete(bucketName, storageKey.value)
                true
            } catch (cause: StorageException) {
                logger.warn("Sletting av dokument med id ${storageKey.value} feilet.", cause)
                false
            }
        }
    }

    override fun lagre(key: StorageKey, value: StorageValue) {
        val blobId = BlobId.of(bucketName, key.value)
        val blobInfo = BlobInfo.newBuilder(blobId).setContentType("text/plain").build()
        lagre(blobInfo, value)
    }

    override fun lagre(key: StorageKey, value: StorageValue, expires: ZonedDateTime) {
        val blobId = BlobId.of(bucketName, key.value)
        val blobInfo = BlobInfo.newBuilder(blobId)
            .setContentType("text/plain")
            .setCustomTime(ZonedDateTime.now(UTC).toInstant().toEpochMilli())
            .setMetadata(
                mapOf(
                    "contentType" to "text/plain",
                    "contentLength" to "${value.value.toByteArray().size.toLong()}"
                )
            )
            .build()

        lagre(blobInfo, value)
    }

    /**
     * Setter metadata for eksisterende objekt.
     *
     * @param key Den unike identifikatoren til objektet.
     * @param metadata metadata å sette på objektet. Dette vil slette eksisterende metadata dersom det allerede eksisterer.
     * @return Returnerer true, dersom objektet blir funnet og oppdatert med ny metadata. False dersom den ikke blir funnet, eller feiler.
     */
    override fun setMetadata(key: StorageKey, metadata: Map<String, String>): Boolean {
        if (hent(key) == null) return false

        return try {
            gcpStorage.get(bucketName, key.value)
                .toBuilder()
                .setCustomTime(ZonedDateTime.now(UTC).plusWeeks(2).toInstant().toEpochMilli())
                .setMetadata(metadata)
                .build()
                .update() != null
        } catch (ex: StorageException) {
            logger.error("Feilet med å sette metadata på objekt med id: ${key.value}", ex)
            return false
        }
    }

    private fun lagre(blobInfo: BlobInfo, value: StorageValue) {
        val content: ByteArray = value.value.toByteArray()
        try {
            gcpStorage.writer(blobInfo).use { writer -> writer.write(ByteBuffer.wrap(content, 0, content.size)) }
        } catch (ex: StorageException) {
            logger.error("Feilet med å lagre dokument med id: ${blobInfo.blobId.name}", ex)
        }
    }

    override fun ready() {
        gcpStorage[bucketName].location
    }

    private fun ensureBucketExists() {
        val bucket = gcpStorage.get(bucketName)
        if (bucket !== null) {
            logger.info("Bucket $bucketName funnet.")
        } else {
            throw IllegalStateException("Fant ikke bucket ved navn $bucketName. Provisjoner en via naiserator.")
        }
    }
}
