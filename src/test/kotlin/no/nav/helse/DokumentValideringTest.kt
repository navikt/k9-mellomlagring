package no.nav.helse

import no.nav.helse.dokument.DokumentEier
import no.nav.helse.dokument.api.ContentTypeService
import no.nav.helse.dokument.api.DokumentDto
import no.nav.helse.dokument.api.valider
import kotlin.test.Test
import java.io.File
import kotlin.test.assertTrue

class DokumentValideringTest {

    val contentTypeService = ContentTypeService()

    // TODO: 23/11/2021 - Fiks hvordan finne path på en annen måte
    val path = "${System.getProperty("user.dir")}/src/test/kotlin/no/nav/helse/pp-dialog.png"

    val gyldigDokumentDto = DokumentDto(
        content = File(path).readBytes(),
        contentType = "image/png",
        title = "Legeerklæring",
        eier = DokumentEier("10047206508")
    )


    @Test
    fun `Validerer gyldig dokument`(){
        val feil = valider(contentTypeService, gyldigDokumentDto)
        assertTrue(feil.isEmpty())
    }

    @Test
    fun `Skal feile dersom det er mismatch mellom oppgitt og faktisk contentType`(){
        val dokument = gyldigDokumentDto.copy(contentType = "application/pdf")
        val feil = valider(contentTypeService, dokument)

        assertTrue(feil.size == 1)
        assertTrue(feil.toString().contains("Mismatch mellom content og contentType"))
    }

    @Test
    fun `Skal feile dersom contentType er av en type som ikke støttes`(){
        val dokument = gyldigDokumentDto.copy(
            contentType = "text/plain",
            content = "plain text".toByteArray()
        )
        val feil = valider(contentTypeService, dokument)

        assertTrue(feil.size == 1)
        assertTrue(feil.toString().contains("Støtter ikke dokument med Content-Type 'text/plain'."))
    }
}
