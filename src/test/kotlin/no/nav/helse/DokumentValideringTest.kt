package no.nav.helse

import no.nav.helse.dokument.DokumentEier
import no.nav.helse.dokument.api.ContentTypeService
import no.nav.helse.dokument.api.DokumentDto
import no.nav.helse.dokument.api.valider
import no.nav.helse.dusseldorf.ktor.core.fromResources
import kotlin.test.Test
import kotlin.test.assertTrue

class DokumentValideringTest {

    val contentTypeService = ContentTypeService()

    val gyldigDokumentDto = DokumentDto(
        content = "pp-dialog.png".fromResources().readBytes(),
        contentType = "image/png",
        title = "Legeerklæring",
        eier = DokumentEier("10047206508")
    )


    @Test
    fun `Validerer gyldig dokument`() {
        val feil = valider(contentTypeService, gyldigDokumentDto)
        assertTrue(feil.isEmpty())
    }

    @Test
    fun `Skal feile dersom det er mismatch mellom oppgitt og faktisk contentType`() {
        val dokument = gyldigDokumentDto.copy(contentType = "application/pdf")
        val feil = valider(contentTypeService, dokument)

        assertTrue(feil.size == 1)
        assertTrue(feil.toString().contains("Filen er egentlig image/png, mens filtypen er application/pdf"))
    }

    @Test
    fun `Skal feile dersom det er mismatch på contentType og filnavn`() {
        val dokument = gyldigDokumentDto.copy(
            contentType = "image/jpeg",
            content = "egentlig-png.png.jpeg".fromResources().readBytes()
        )
        val feil = valider(contentTypeService, dokument)

        assertTrue(feil.size == 1)
        assertTrue(feil.toString().contains("Filen er egentlig image/png, mens filtypen er image/jpeg"))
    }

    @Test
    fun `Skal feile dersom contentType er av en type som ikke støttes`() {
        val dokument = gyldigDokumentDto.copy(
            contentType = "text/plain",
            content = "plain text".toByteArray()
        )
        val feil = valider(contentTypeService, dokument)

        assertTrue(feil.size == 1)
        assertTrue(feil.toString().contains("Støtter ikke dokument med Content-Type 'text/plain'."))
    }
}
