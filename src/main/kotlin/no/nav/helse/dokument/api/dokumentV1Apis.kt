package no.nav.helse.dokument.api

import com.fasterxml.jackson.annotation.JsonAlias
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.helse.dokument.Dokument
import no.nav.helse.dokument.DokumentEier
import no.nav.helse.dokument.DokumentId
import no.nav.helse.dokument.DokumentService
import no.nav.helse.dokument.eier.EierResolver
import no.nav.helse.dusseldorf.ktor.auth.ClaimRule
import no.nav.helse.dusseldorf.ktor.auth.Issuer
import no.nav.helse.dusseldorf.ktor.core.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime

private val logger: Logger = LoggerFactory.getLogger("nav.dokumentApis")
private const val BASE_PATH = "v1/dokument"
private const val MAX_DOKUMENT_SIZE = 8 * 1024 * 1024

private const val CONTENT_PART_NAME = "content"
private const val TITLE_PART_NAME = "title"
private const val EIER_PART_NAME = "eier"

internal fun Route.dokumentV1Apis(
    dokumentService: DokumentService,
    eierResolver: EierResolver,
    contentTypeService: ContentTypeService,
    baseUrl: String,
    issuers: Map<Issuer, Set<ClaimRule>>
) {
    val azureV1Issuer = issuers.filterKeys { it.alias() == "azure-v1" }.entries.first().key.issuer()
    val azureV2Issuer = issuers.filterKeys { it.alias() == "azure-v2" }.entries.first().key.issuer()
    val loginServiceV1Issuer = issuers.filterKeys { it.alias() == "login-service-v1" }.entries.first().key.issuer()
    val loginServiceV2Issuer = issuers.filterKeys { it.alias() == "login-service-v2" }.entries.first().key.issuer()

    post(BASE_PATH) {
        logger.info("Lagrer dokument")
        logger.trace("Henter dokument fra requesten")
        val dokument: DokumentDto = call.hentDokumentFraRequest()
        val violations = valider(contentTypeService = contentTypeService, dokument = dokument)
        if (violations.isNotEmpty()) {
            throw Throwblem(ValidationProblemDetails(violations))
        }

        val principal: JWTPrincipal = call.principal() ?: throw IllegalStateException("Principal ikke satt.")

        logger.trace("Dokument hetent fra reqeusten, forsøker å lagre")
        val eier = eierResolver.hentEier(principal, dokument.eier!!.eiersFødselsnummer)

        val dokumentId = when (val issuer = principal.payload.issuer) {
            azureV1Issuer, azureV2Issuer -> dokumentService.lagreDokument(
                dokument = dokument.tilDokument(),
                eier = eier
            )
            loginServiceV1Issuer -> dokumentService.lagreDokument(
                dokument = dokument.tilDokument(),
                eier = eier,
                expires = ZonedDateTime.now().plusMinutes(1)
            )
            loginServiceV2Issuer -> dokumentService.lagreDokument(
                dokument = dokument.tilDokument(),
                eier = eier,
                expires = ZonedDateTime.now().plusMinutes(1)
            )
            else -> throw IllegalArgumentException("Ikke støttet issuer $issuer")
        }

        logger.trace("Dokument lagret.")
        logger.info("$dokumentId")

        call.respondCreatedDokument(baseUrl, dokumentId)
    }

    post("$BASE_PATH/{dokumentId}") {
        logger.info("Henter dokument")
        val dokumentId = call.dokumentId()
        val dokumentEier = call.dokumentEier()
        val etterspurtJson = call.request.etterspurtJson()
        logger.info("$dokumentId")

        logger.trace("EtterspurtJson=$etterspurtJson")

        val principal: JWTPrincipal = call.principal() ?: throw IllegalStateException("Principal ikke satt.")

        val dokument = dokumentService.hentDokument(
            dokumentId = call.dokumentId(),
            eier = eierResolver.hentEier(principal, dokumentEier.eiersFødselsnummer)
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
        val dokumentEier = call.dokumentEier()
        logger.info("Sletter dokument")
        logger.info("$dokumentId")

        val principal: JWTPrincipal = call.principal() ?: throw IllegalStateException("Principal ikke satt.")

        val result = dokumentService.slettDokument(
            dokumentId = dokumentId,
            eier = eierResolver.hentEier(principal, dokumentEier.eiersFødselsnummer)
        )

        when {
            result -> call.respond(HttpStatusCode.NoContent)
            else -> call.respond(HttpStatusCode.NotFound)
        }
    }

    put("$BASE_PATH/{dokumentId}/persister") {
        val principal: JWTPrincipal = call.principal() ?: throw IllegalStateException("Principal ikke satt.")
        val issuer = principal.payload.issuer
        if (issuer == loginServiceV1Issuer || issuer == loginServiceV2Issuer) {
            call.respondForbiddenAccess(issuer)
            return@put
        }

        val dokumentId = call.dokumentId()
        val dokumentEier = call.dokumentEier()
        logger.info("Persisterer dokument med id: {}", dokumentId.id)

        val result = when (issuer) {
            azureV1Issuer, azureV2Issuer -> dokumentService.persister(
                dokumentId = dokumentId,
                eier = eierResolver.hentEier(principal, dokumentEier.eiersFødselsnummer)
            )
            else -> throw IllegalArgumentException("Ikke støttet issuer $issuer")
        }

        when(result) {
            true -> call.respond(HttpStatusCode.NoContent)
            false -> call.respond(HttpStatusCode.NotFound)
        }
    }
}

private fun valider(
    contentTypeService: ContentTypeService,
    dokument: DokumentDto
): Set<Violation> {
    logger.trace("Validerer dokumentet")
    val violations = dokument.valider()
    if (!contentTypeService.isSupported(contentType = dokument.contentType!!, content = dokument.content!!)) {
        violations.add(
            Violation(
                parameterName = HttpHeaders.ContentType,
                reason = "Ikke Supportert dokument med Content-Type ${dokument.contentType}",
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
    val url = URLBuilder(baseUrl).path(BASE_PATH, dokumentId.id).build().toString()
    response.header(HttpHeaders.Location, url)
    respond(HttpStatusCode.Created, mapOf(Pair("id", dokumentId.id)))
}

internal data class DokumentDto(
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
