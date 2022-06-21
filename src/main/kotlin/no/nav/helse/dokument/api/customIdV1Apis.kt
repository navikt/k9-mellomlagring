package no.nav.helse.dokument.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import no.nav.helse.dokument.Dokument
import no.nav.helse.dokument.DokumentService
import no.nav.helse.dokument.eier.EierResolver
import no.nav.helse.dusseldorf.ktor.auth.idToken
import no.nav.helse.dusseldorf.ktor.core.Throwblem
import no.nav.helse.dusseldorf.ktor.core.ValidationProblemDetails
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime

private val logger: Logger = LoggerFactory.getLogger("nav.customIdV1Apis")
private const val BASE_PATH = "v1/dokument/customized/{customDokumentId}"
private val CUSTOM_DOKUMENT_ID_REGEX = "^[a-zA-Z0-9]{3,50}\$".toRegex()

internal fun Route.customIdV1Apis(
    støtterExpirationFraRequest: Boolean,
    dokumentService: DokumentService,
    eierResolver: EierResolver,
    contentTypeService: ContentTypeService
) {

    put(BASE_PATH) {
        val customDokumentId = call.customDokumentId()
        logger.info("Lagrer dokument for CustomDokumentId=${customDokumentId.id}")
        val expires = call.expires(støtterExpirationFraRequest)
        logger.info("Expires=$expires")
        val dokument = call.hentDokumentFraRequest()

        val contentType = contentTypeService.getContentType(
            contentType = dokument.contentType,
            content = dokument.content
        )
        val idToken = call.idToken()
        when (contentType) {
            ContentTypeService.JSON -> {
                dokumentService.lagreDokument(
                    customDokumentId = customDokumentId,
                    dokument = dokument,
                    eier = eierResolver.hentEier(idToken, dokument.eier.eiersFødselsnummer)
                )
                call.respond(HttpStatusCode.NoContent)
            }
            else -> {
                call.respond(HttpStatusCode.BadRequest)
            }
        }
    }

    get(BASE_PATH) {
        val customDokumentId = call.customDokumentId()
        logger.info("Henter dokument for CustomDokumentId=${customDokumentId.id}")

        val dokumentEier = call.dokumentEier()
        val idToken = call.idToken()
        val dokument = dokumentService.hentDokument(
            customDokumentId = customDokumentId,
            eier = eierResolver.hentEier(idToken, dokumentEier.eiersFødselsnummer)
        )

        when {
            dokument == null -> call.respond(HttpStatusCode.NotFound)
            call.request.etterspurtJson() -> call.respond(HttpStatusCode.OK, dokument)
            else -> call.respond(HttpStatusCode.BadRequest)
        }
    }
}

data class CustomDokumentId(val id: String)

private fun ApplicationCall.customDokumentId() : CustomDokumentId {
    val customDokumentId = parameters.getOrFail("customDokumentId")
    return when (CUSTOM_DOKUMENT_ID_REGEX.matches(customDokumentId)) {
        true -> CustomDokumentId(customDokumentId)
        false -> throw IllegalArgumentException("Ugyldig ID $customDokumentId.")
    }
}
private fun ApplicationCall.expires(støtterExpirationFraRequest: Boolean) : ZonedDateTime? {
    val expires = request.header("Expires") ?: return null
    return when (støtterExpirationFraRequest) {
        true -> ZonedDateTime.parse(expires)
        false -> throw IllegalArgumentException("Støtter ikke expires fra request. Vars satt til $expires.")
    }
}
private suspend fun ApplicationCall.hentDokumentFraRequest(): Dokument {
    return when (request.contentType() == ContentType.Application.Json) {
        true -> {
            val dto = receive<DokumentDto>()
            val violations = dto.valider()
            when (violations.isEmpty()) {
                true -> dto.tilDokument()
                else -> throw Throwblem(ValidationProblemDetails(violations.toSet()))
            }
        }
        else -> throw  IllegalArgumentException("Støtter ikke ContentType=${request.header(HttpHeaders.ContentType)}")
    }
}


