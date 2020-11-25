package no.nav.helse.dokument.storage

import com.google.cloud.storage.Storage.BlobField

class InMemoryStorage : Storage {
    override fun ready() {}

    private val storage = mutableMapOf<StorageKey, StorageValue>()
    private val metadata = mutableMapOf<StorageKey, Map<String, Any>>()

    override fun slett(storageKey: StorageKey): Boolean {
        val value = storage.remove(storageKey)
        return value != null
    }

    override fun lagre(key: StorageKey, value: StorageValue, hold: Boolean) {
        storage[key] = value
    }

    override fun persister(key: StorageKey): Boolean {
        this.storage[key] ?: throw IllegalStateException("Fant ikke dokument å persistere.")
        metadata[key] = mutableMapOf(BlobField.TEMPORARY_HOLD.name to true)
        return harHold(key)
    }

    override fun harHold(key: StorageKey): Boolean {
        val metadata = this.metadata[key]!!
        return when(metadata[BlobField.TEMPORARY_HOLD.name] as Boolean?) {
            true -> true
            else -> false
        }
    }

    override fun hent(key: StorageKey): StorageValue? {
        return storage[key]
    }
}
