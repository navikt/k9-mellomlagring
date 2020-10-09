package no.nav.helse

import no.nav.helse.dokument.Dokument
import no.nav.helse.dokument.DokumentSerDes
import org.skyscreamer.jsonassert.JSONAssert
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class DokumentSerDesTest {

    private companion object {
        const val contentString = "Dette er noe innhold"
        val contentByteArray = contentString.toByteArray()
        val contentBase64Encoded = Base64.getEncoder().encodeToString(contentByteArray)!!
    }
    @Test
    fun `Serialize & Deserialize Dokument`() {
        val dokument = Dokument(
            title = "Dette er en tittel",
            content = contentByteArray,
            contentType = "application/json"
        )

        val serialized = DokumentSerDes.serialize(dokument)
        JSONAssert.assertEquals("""
            {
                "title": "Dette er en tittel",
                "content": "$contentBase64Encoded",
                "content_type": "application/json"
            }
        """.trimIndent(), serialized, true)


        val deserialized = DokumentSerDes.deserialize(serialized)
        assertEquals(dokument, deserialized)
    }
}