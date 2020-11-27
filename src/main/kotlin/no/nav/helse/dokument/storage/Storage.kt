package no.nav.helse.dokument.storage

interface Storage {
    fun hent(key : StorageKey) : StorageValue?
    fun slett(storageKey: StorageKey) : Boolean
    fun lagre(key: StorageKey, value: StorageValue, hold: Boolean)
    fun persister(key: StorageKey): Boolean
    fun harHold(key: StorageKey): Boolean
    fun ready()
}

data class StorageKey(val value: String)
data class StorageValue(val value: String)
