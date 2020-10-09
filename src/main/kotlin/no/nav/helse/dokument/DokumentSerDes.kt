package no.nav.helse.dokument

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.k9MellomlagringConfigured

class DokumentSerDes {
    companion object {
        private val objectMapper = jacksonObjectMapper().k9MellomlagringConfigured()
        internal fun serialize(dokument: Dokument) : String =  objectMapper.writeValueAsString(dokument)
        internal fun deserialize(string: String) : Dokument = objectMapper.readValue(string)
    }
}
