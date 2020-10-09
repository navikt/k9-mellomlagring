package no.nav.helse.dokument.api

import com.fasterxml.jackson.annotation.JsonAlias
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.readAllParts
import io.ktor.http.content.streamProvider
import io.ktor.request.*
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.response.respondBytes
import io.ktor.routing.*
import no.nav.helse.dokument.Dokument
import no.nav.helse.dokument.DokumentId
import no.nav.helse.dokument.DokumentService
import no.nav.helse.dokument.eier.EierResolver
import no.nav.helse.dusseldorf.ktor.core.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger("nav.dokumentApis")
private const val BASE_PATH = "v1/dokument"
private const val MAX_DOKUMENT_SIZE = 8 * 1024 * 1024

private const val CONTENT_PART_NAME = "content"
private const val TITLE_PART_NAME = "title"

internal fun Route.dokumentV1Apis(
    dokumentService: DokumentService,
    eierResolver: EierResolver,
    contentTypeService: ContentTypeService,
    baseUrl: String
) {

    post(BASE_PATH) {
        logger.info("Lagrer dokument")
        logger.trace("Henter dokument fra requesten")
        val dokument = call.hentDokumentFraRequest()
        val violations = valider(contentTypeService = contentTypeService, dokument = dokument)
        if (violations.isNotEmpty()) {
            throw Throwblem(ValidationProblemDetails(violations))
        }

        logger.trace("Dokument hetent fra reqeusten, forsøker å lagre")
        val dokumentId = dokumentService.lagreDokument(
            dokument = dokument.tilDokument(),
            eier = eierResolver.hentEier(call)
        )
        logger.trace("Dokument lagret.")
        logger.info("$dokumentId")

        call.respondCreatedDokument(baseUrl, dokumentId)
    }

    get("$BASE_PATH/{dokumentId}") {
        val dokumentId = call.dokumentId()
        val etterspurtJson = call.request.etterspurtJson()
        logger.info("Henter dokument")
        logger.info("$dokumentId")

        logger.trace("EtterspurtJson=$etterspurtJson")

        val dokument = dokumentService.hentDokument(
            dokumentId = call.dokumentId(),
            eier = eierResolver.hentEier(call)
        )

        logger.trace("FantDokment=${dokument != null}")

        when {
            dokument == null -> call.respondDokumentNotFound(dokumentId)
            etterspurtJson -> call.respond(HttpStatusCode.OK, dokument)
            else -> call.respondBytes(
                bytes = dokument.content,
                contentType = ContentType.parse(dokument.contentType),
                status = HttpStatusCode.OK
            )
        }
    }

    delete("$BASE_PATH/{dokumentId}") {
        val dokumentId = call.dokumentId()
        logger.info("Sletter dokument")
        logger.info("$dokumentId")

        val result = dokumentService.slettDokument(
            dokumentId = dokumentId,
            eier = eierResolver.hentEier(call)
        )

        when {
            result -> call.respond(HttpStatusCode.NoContent)
            else -> call.respond(HttpStatusCode.NotFound)
        }
    }
}

private fun valider(
    contentTypeService: ContentTypeService,
    dokument: DokumentDto
) : Set<Violation> {
    logger.trace("Validerer dokumentet")
    val violations = dokument.valider()
    if (!contentTypeService.isSupported(contentType = dokument.contentType!!, content = dokument.content!!)) {
        violations.add(Violation(parameterName = HttpHeaders.ContentType, reason = "Ikke Supportert dokument med Content-Type ${dokument.contentType}", parameterType = ParameterType.HEADER))
    }
    return violations.toSet()
}

private suspend fun ApplicationCall.hentDokumentFraRequest(): DokumentDto {
    return if (request.isMultipart()) {
        logger.trace("Behandler multipart request")
        receiveMultipart().getDokumentDto()
    } else  {
        logger.trace("Behandler json request")
        receive()
    }
}

private fun ApplicationCall.dokumentId() : DokumentId {
    return DokumentId(parameters["dokumentId"]!!)
}

internal fun ApplicationRequest.etterspurtJson() : Boolean {
    return ContentType.Application.Json.toString() == accept()
}

private suspend fun MultiPartData.getDokumentDto() : DokumentDto {
    var content : ByteArray? = null
    var contentType : String? = null
    var title : String? = null

    for (partData in readAllParts()) {
        if (partData is PartData.FileItem && CONTENT_PART_NAME == partData.name) {
            content = partData.streamProvider().use { it.readBytes() }
            contentType = partData.contentType.toString()
        } else if (partData is PartData.FormItem && TITLE_PART_NAME == partData.name) {
            title = partData.value
        }
        partData.dispose()
    }

    return DokumentDto(content, contentType, title)
}

private suspend fun ApplicationCall.respondDokumentNotFound(dokumentId : DokumentId) {

    val problemDetails = DefaultProblemDetails(
        status = 404,
        title = "document-not-found",
        detail = "Dokument med ID ${dokumentId.id} ikke funnet."
    )
    respond(HttpStatusCode.NotFound, problemDetails)
}

private suspend fun ApplicationCall.respondCreatedDokument(baseUrl : String, dokumentId: DokumentId) {
    val url = URLBuilder(baseUrl).path(BASE_PATH,dokumentId.id).build().toString()
    response.header(HttpHeaders.Location, url)
    respond(HttpStatusCode.Created, mapOf(Pair("id", dokumentId.id)))
}

internal data class DokumentDto(val content: ByteArray?, @JsonAlias("contentType") val contentType: String?, val title : String?) {
    fun valider() : MutableList<Violation> {
        val violations = mutableListOf<Violation>()
        if (content == null) violations.add(Violation(parameterName = CONTENT_PART_NAME, reason = "Fant ingen 'part' som er en fil.", parameterType = ParameterType.ENTITY))
        if (content != null && content.size > MAX_DOKUMENT_SIZE) violations.add(Violation(parameterName = CONTENT_PART_NAME, reason = "Dokumentet er større en maks tillat 8MB.", parameterType = ParameterType.ENTITY))
        if (contentType == null) violations.add(Violation(parameterName = HttpHeaders.ContentType, reason = "Ingen Content-Type satt på fil.", parameterType = ParameterType.ENTITY))
        if (title == null) violations.add(Violation(parameterName = TITLE_PART_NAME, reason = "Fant ingen 'part' som er en form item.", parameterType = ParameterType.ENTITY))
        return violations
    }
    fun tilDokument() : Dokument {
        return Dokument(
            content = content!!,
            contentType = contentType!!,
            title = title!!
        )
    }
}
