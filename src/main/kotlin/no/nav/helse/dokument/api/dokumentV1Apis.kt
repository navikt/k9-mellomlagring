package no.nav.helse.dokument.api

import com.fasterxml.jackson.annotation.JsonAlias
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.dokument.Dokument
import no.nav.helse.dokument.DokumentEier
import no.nav.helse.dokument.DokumentId
import no.nav.helse.dokument.DokumentService
import no.nav.helse.dokument.eier.EierResolver
import no.nav.helse.dusseldorf.ktor.auth.idToken
import no.nav.helse.dusseldorf.ktor.core.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger("nav.dokumentApis")
private const val BASE_PATH = "v1/dokument"
private const val MAX_DOKUMENT_SIZE = 10 * 1024 * 1024

private const val CONTENT_PART_NAME = "content"
private const val TITLE_PART_NAME = "title"
private const val EIER_PART_NAME = "eier"

internal fun Route.dokumentV1Apis(
    dokumentService: DokumentService,
    eierResolver: EierResolver,
    contentTypeService: ContentTypeService,
    baseUrl: String
) {

    post(BASE_PATH) {
        logger.info("Lagrer dokument")
        logger.trace("Henter dokument fra requesten")
        val dokument: DokumentDto = call.hentDokumentFraRequest()
        val violations = valider(contentTypeService = contentTypeService, dokument = dokument)
        if (violations.isNotEmpty()) {
            throw Throwblem(ValidationProblemDetails(violations))
        }

        val idToken = call.idToken()

        logger.trace("Dokument hentet fra reqeusten, forsøker å lagre")
        val eier = eierResolver.hentEier(idToken, dokument.eier!!.eiersFødselsnummer)

        val dokumentId = when {
            idToken.issuerIsAzure() -> dokumentService.lagreDokument(
                dokument = dokument.tilDokument(),
                eier = eier,
                medHold = true
            )
            idToken.issuerIsLoginservice() || idToken.issuerIsTokendings() || idToken.issuerIsIDPorten() -> dokumentService.lagreDokument(
                dokument = dokument.tilDokument(),
                eier = eier
            )
            else -> throw IllegalArgumentException("Ikke støttet issuer ${idToken.issuer()}")
        }

        logger.info("Dokument lagret med id: {}", dokumentId)
        call.respondCreatedDokument(baseUrl, dokumentId)
    }

    post("$BASE_PATH/{dokumentId}") {
        logger.info("Henter dokument")
        val dokumentId = call.dokumentId()
        val dokumentEier = call.dokumentEier()
        val etterspurtJson = call.request.etterspurtJson()
        logger.info("$dokumentId")

        logger.trace("EtterspurtJson=$etterspurtJson")

        val idToken = call.idToken()

        val dokument = dokumentService.hentDokument(
            dokumentId = call.dokumentId(),
            eier = eierResolver.hentEier(idToken, dokumentEier.eiersFødselsnummer)
        )

        logger.trace("FantDokment=${dokument != null}")

        when {
            dokument == null -> {
                logger.error("Dokument med id ikke funnet: {}", dokumentId)
                call.respondDokumentNotFound(dokumentId)
            }
            etterspurtJson -> {
                logger.info("Dokument(etterspurtJson) med id funnet: {}", dokumentId)
                call.respond(HttpStatusCode.OK, dokument)
            }
            else -> {
                logger.info("Dokument(etterspurtBytes) med id funnet: {}", dokumentId)
                call.respondBytes(
                    bytes = dokument.content,
                    contentType = ContentType.parse(dokument.contentType),
                    status = HttpStatusCode.OK
                )
            }
        }
    }

    delete("$BASE_PATH/{dokumentId}") {
        val dokumentId = call.dokumentId()
        val dokumentEier = call.dokumentEier()
        logger.info("Sletter dokument med id: {}", dokumentId)

        val idToken = call.idToken()
        val eier = eierResolver.hentEier(idToken, dokumentEier.eiersFødselsnummer)

        val result = dokumentService.slettDokument(
            dokumentId = dokumentId,
            eier = eier
        )

        when (result) {
            true -> {
                logger.info("Dokument med id slettet: {}", dokumentId)
                call.respond(HttpStatusCode.NoContent)
            }
            false -> {
                logger.warn("Dokument med id ikke funnet: {}", dokumentId)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }

    put("$BASE_PATH/persistert/{dokumentId}") {
        val dokumentId = call.dokumentId()
        val dokumentEier = call.dokumentEier()
        logger.info("Sletter hold på persistert dokument med id: {}", dokumentId)

        val idToken = call.idToken()
        val eier = eierResolver.hentEier(idToken, dokumentEier.eiersFødselsnummer)

        val resultat = dokumentService.fjerneHoldPåPersistertDokument(
            dokumentId = dokumentId,
            eier = eier
        )

        when (resultat) {
            true -> {
                logger.info("Fjernet hold på dokument med id: {}", dokumentId)
                call.respond(HttpStatusCode.OK)
            }
            false -> {
                logger.info("Dokument med id ikke funnet: {}", dokumentId)
                call.respond(HttpStatusCode.NotFound)
            }
        }

        call.respond(HttpStatusCode.OK)
    }

    put("$BASE_PATH/{dokumentId}/persister") {
        val idToken = call.idToken()
        if (idToken.issuerIsLoginservice() || idToken.issuerIsTokendings()) {
            call.respondForbiddenAccess(idToken.issuer())
            return@put
        }

        val dokumentId = call.dokumentId()
        val dokumentEier = call.dokumentEier()
        logger.info("Persisterer dokument med id: {}", dokumentId)

        val result = when {
            idToken.issuerIsAzure() -> dokumentService.persister(
                dokumentId = dokumentId,
                eier = eierResolver.hentEier(idToken, dokumentEier.eiersFødselsnummer)
            )
            else -> throw IllegalArgumentException("Ikke støttet issuer ${idToken.issuer()}")
        }

        when (result) {
            true -> {
                logger.info("Dokument med id persistert: {}", dokumentId)
                call.respond(HttpStatusCode.NoContent)
            }
            false -> {
                logger.info("Dokument med id ikke funnet: {}", dokumentId)
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}

fun valider(
    contentTypeService: ContentTypeService,
    dokument: DokumentDto
): Set<Violation> {
    logger.trace("Validerer dokumentet")
    val violations = dokument.valider()

    if (dokument.content != null && dokument.contentType != null) {
        if (!contentTypeService.isWhatItSeems(dokument.content, dokument.contentType)) {
            violations.add(
                Violation(
                    parameterName = HttpHeaders.ContentType,
                    reason = "Mismatch mellom content og contentType",
                    invalidValue = dokument.contentType,
                    parameterType = ParameterType.HEADER
                )
            )
        }
    }

    if (!contentTypeService.isSupportedContentType(contentType = dokument.contentType!!)) {
        violations.add(
            Violation(
                parameterName = HttpHeaders.ContentType,
                reason = "Støtter ikke dokument med Content-Type '${dokument.contentType}'. ",
                parameterType = ParameterType.HEADER
            )
        )
    }
    return violations.toSet()
}

private suspend fun ApplicationCall.hentDokumentFraRequest(): DokumentDto {
    logger.trace("Behandler json request")
    return receive()
}

private fun ApplicationCall.dokumentId(): DokumentId {
    return DokumentId(parameters["dokumentId"]!!)
}

suspend fun ApplicationCall.dokumentEier(): DokumentEier {
    return receive()
}

internal fun ApplicationRequest.etterspurtJson(): Boolean {
    return ContentType.Application.Json.toString() == accept()
}

private suspend fun ApplicationCall.respondDokumentNotFound(dokumentId: DokumentId) {

    val problemDetails = DefaultProblemDetails(
        status = 404,
        title = "document-not-found",
        detail = "Dokument med ID ${dokumentId.id} ikke funnet."
    )
    respond(HttpStatusCode.NotFound, problemDetails)
}

private suspend fun ApplicationCall.respondForbiddenAccess(issuer: String) {

    val problemDetails = DefaultProblemDetails(
        status = 403,
        title = "issuer-not-allowed",
        detail = "Issuer: $issuer er ikke tillatt på dette endepunktet. Det er kun tillatt med Azure issuers."
    )
    respond(HttpStatusCode.Forbidden, problemDetails)
}

private suspend fun ApplicationCall.respondCreatedDokument(baseUrl: String, dokumentId: DokumentId) {
    val urlBuilder = URLBuilder(baseUrl)
    urlBuilder.path(BASE_PATH, dokumentId.id)
    val url = urlBuilder.build().toString()
    response.header(HttpHeaders.Location, url)
    respond(HttpStatusCode.Created, mapOf(Pair("id", dokumentId.id)))
}

data class DokumentDto(
    val content: ByteArray?,
    @JsonAlias("contentType") val contentType: String?,
    val title: String?,
    val eier: DokumentEier?
) {
    fun valider(): MutableList<Violation> {
        val violations = mutableListOf<Violation>()
        if (content == null) violations.add(
            Violation(
                parameterName = CONTENT_PART_NAME,
                reason = "Fant ingen 'part' som er en fil.",
                parameterType = ParameterType.ENTITY
            )
        )
        if (content != null && content.size > MAX_DOKUMENT_SIZE) violations.add(
            Violation(
                parameterName = CONTENT_PART_NAME,
                reason = "Dokumentet er større en maks tillat 8MB.",
                parameterType = ParameterType.ENTITY
            )
        )
        if (contentType == null) violations.add(
            Violation(
                parameterName = HttpHeaders.ContentType,
                reason = "Ingen Content-Type satt på fil.",
                parameterType = ParameterType.ENTITY
            )
        )
        if (title == null) violations.add(
            Violation(
                parameterName = TITLE_PART_NAME,
                reason = "Fant ingen 'part' som er en form item.",
                parameterType = ParameterType.ENTITY
            )
        )
        if (eier == null) violations.add(
            Violation(
                parameterName = EIER_PART_NAME,
                reason = "Fant ingen 'part' som er en form item.",
                parameterType = ParameterType.ENTITY
            )
        )
        if (eier != null && eier.eiersFødselsnummer.isBlank()) violations.add(
            Violation(
                parameterName = EIER_PART_NAME,
                reason = "Fant ingen 'part' som er en form item.",
                parameterType = ParameterType.ENTITY
            )
        )
        return violations
    }

    fun tilDokument(): Dokument {
        return Dokument(
            content = content!!,
            contentType = contentType!!,
            title = title!!,
            eier = eier!!
        )
    }
}
