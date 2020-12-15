package no.nav.helse.dokument.storage

import com.google.cloud.storage.Blob
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.StorageException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import com.google.cloud.storage.Storage as GcpStorage


class GcpStorageBucket(
    private val gcpStorage: GcpStorage,
    private val bucketName: String
) : Storage {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(GcpStorageBucket::class.java)
    }

    init {
        ensureBucketExists()
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

        if (harHold(storageKey)) toggleHold(storageKey, false)

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

    override fun lagre(key: StorageKey, value: StorageValue, hold: Boolean) {
        val blobId = BlobId.of(bucketName, key.value)
        val blobInfo = BlobInfo.newBuilder(blobId).setContentType("text/plain").build()
        lagre(blobInfo, value)

        if (hold) toggleHold(key, hold)
    }

    private fun toggleHold(key: StorageKey, hold: Boolean) {
        val blobInfo = hentBlobInfoBuilder(key).setTemporaryHold(hold).build()
        gcpStorage.update(blobInfo)
        if(hold) logger.info("Midlertidig hold er plassert på dokument med id: ${key.value}")
        else logger.info("Midlertidig hold er fjernet på dokument med id: ${key.value}")
    }

    override fun harHold(key: StorageKey): Boolean {
        val blobId = BlobId.of(bucketName, key.value)
        val blob: Blob = gcpStorage.get(blobId, GcpStorage.BlobGetOption.fields(GcpStorage.BlobField.TEMPORARY_HOLD))

        return when(blob.temporaryHold){
            true -> true
            else -> false
        }
    }

    private fun hentBlobInfoBuilder(key: StorageKey): BlobInfo.Builder {
        val blobId = BlobId.of(bucketName, key.value)
        return BlobInfo.newBuilder(blobId)
    }

    /**
     * Setter metadata 'customDateTime' for eksisterende objekt.
     * Dette kallet setter customDateTime for objektet til året 999_999_999, eller inntil den er slettet.
     *
     * @param key Den unike identifikatoren til objektet.
     * @return Returnerer true, dersom objektet blir funnet og oppdatert med ny metadata. False dersom den ikke blir funnet, eller feiler.
     */
    override fun persister(key: StorageKey): Boolean {
        if (hent(key) == null) return false

        return try {
            toggleHold(key, true)
            return true
        } catch (ex: StorageException) {
            logger.error("Feilet med å persistere objekt med id: ${key.value}", ex)
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
