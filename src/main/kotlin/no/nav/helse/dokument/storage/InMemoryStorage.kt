package no.nav.helse.dokument.storage

import java.time.ZonedDateTime

class InMemoryStorage : Storage {
    override fun ready() {}

    private val storage = mutableMapOf<StorageKey, StorageValue>()
    private val metadata = mutableMapOf<StorageKey, Map<String, String>>()

    override fun slett(storageKey: StorageKey): Boolean {
        val value = storage.remove(storageKey)
        return value != null
    }

    override fun lagre(key: StorageKey, value: StorageValue) {
        storage[key] = value
    }

    override fun lagre(key: StorageKey, value: StorageValue, expires: ZonedDateTime) {
        lagre(key, value)
    }

    override fun setMetadata(key: StorageKey, metadata: Map<String, String>): Boolean {
        if (this.storage[key] == null) return false

        this.metadata[key] = metadata
        return this.metadata[key] != null
    }

    override fun hent(key: StorageKey): StorageValue? {
        return storage[key]
    }
}
