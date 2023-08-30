package no.nav.helse.dokument

import com.fasterxml.jackson.annotation.JsonAlias
import no.nav.helse.dokument.crypto.Cryptography
import no.nav.helse.dokument.eier.Eier
import no.nav.helse.dokument.storage.Storage
import no.nav.helse.dokument.storage.StorageKey
import no.nav.helse.dokument.storage.StorageValue
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger("nav.DokumentService")

data class DokumentService(
    private val cryptography: Cryptography,
    private val storage: Storage,
    private val virusScanner: VirusScanner?
) {
    init {
        if (virusScanner == null) logger.info("Virusscanning av dokumenter før lagring er skrudd av.")
        else logger.info("Virusscanning av dokumenter blir gjort før lagring.")
    }

    fun hentDokument(
        dokumentId: DokumentId,
        eier: Eier
    ): Dokument? {
        logger.trace("Henter dokument $dokumentId.")
        val value = storage.hent(
            generateStorageKey(
                dokumentId = dokumentId,
                eier = eier
            )
        ) ?: return null

        logger.trace("Fant dokument, dekrypterer.")

        val decrypted = cryptography.decrypt(
            id = dokumentId.id,
            encrypted = value.value,
            eier = eier
        )

        logger.trace("Dekryptert, mapper til dokument.")

        return DokumentSerDes.deserialize(decrypted)
    }

    fun slettDokument(
        dokumentId: DokumentId,
        eier: Eier
    ): Boolean {
        logger.trace("Sletter dokument $dokumentId")
        val result = storage.slett(
            generateStorageKey(
                dokumentId = dokumentId,
                eier = eier
            )
        )
        if (!result) logger.warn("Fant ikke noe dokument å slette.")
        return result
    }

    fun persister(
        dokumentId: DokumentId,
        eier: Eier
    ): Boolean {
        logger.info("Setter metadata på dokument med id: {}", dokumentId)
        val key = generateStorageKey(dokumentId, eier)
        return storage.persister(key)
    }

    suspend fun lagreDokument(
        dokument: Dokument,
        eier: Eier,
        medHold: Boolean = false
    ): DokumentId {
        virusScanner?.scan(dokument)

        logger.trace("Generer DokumentID")
        val dokumentId = generateDokumentId()
        logger.trace("DokumentID=$dokumentId. Krypterer.")

        val encrypted = cryptography.encrypt(
            id = dokumentId.id,
            plainText = DokumentSerDes.serialize(dokument),
            eier = eier
        )

        logger.trace("Larer dokument.")

        storage.lagre(
            key = generateStorageKey(
                dokumentId = dokumentId,
                eier = eier
            ),
            value = StorageValue(
                value = encrypted
            ),
            hold = medHold
        )

        logger.trace("Lagring OK.")

        return dokumentId
    }

    fun fjerneHoldPåPersistertDokument(
        dokumentId: DokumentId,
        eier: Eier
    ): Boolean {
        return storage.fjerneHold(
            generateStorageKey(
                dokumentId = dokumentId,
                eier = eier
            )
        )
    }

    fun dokumentHarHold(dokumentId: DokumentId, eier: Eier): Boolean = storage.harHold(generateStorageKey(dokumentId, eier))

    private fun generateStorageKey(
        dokumentId: DokumentId,
        eier: Eier
    ): StorageKey {
        logger.trace("Genrerer Storage Key for $dokumentId. Krypterer.")
        val plainText = "${eier.id}-${dokumentId.id}"
        val encrypted = cryptography.encrypt(
            id = dokumentId.id,
            plainText = plainText,
            eier = eier
        )
        logger.trace("Storage Key kryptert.")
        val storageKey = StorageKey(
            value = encrypted
        )
        logger.info("$storageKey")
        return storageKey
    }

    private fun generateDokumentId(): DokumentId = DokumentId(id = cryptography.id())
}

data class DokumentId(val id: String)

data class Dokument(
    val title: String,
    val eier: DokumentEier,
    val content: ByteArray,
    @JsonAlias("contentType") val contentType: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Dokument

        if (title != other.title) return false
        if (eier != other.eier) return false
        if (!content.contentEquals(other.content)) return false
        if (contentType != other.contentType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + content.contentHashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + eier.hashCode()
        return result
    }
}

data class DokumentEier(
    val eiersFødselsnummer: String
)
