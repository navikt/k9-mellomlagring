package no.nav.helse.dokument.eier

import io.ktor.application.ApplicationCall
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.principal
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class EierResolver(
    private val hentEierFra: HentEierFra
) {
    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(EierResolver::class.java)
    }

    init {
        logger.info("HentEierFra=${hentEierFra.name}")
    }

    internal fun hentEier(call: ApplicationCall) : Eier = when (hentEierFra) {
        HentEierFra.ACCESS_TOKEN_SUB_CLAIM -> eierFraPrincipal(call)
        HentEierFra.QUERY_PARAMETER_EIER -> eierFraQuery(call)
    }

    private fun eierFraPrincipal(call: ApplicationCall) : Eier {
        logger.trace("Forsøker å hente eier fra JWT token 'sub' claim")
        val jwtPrincipal : JWTPrincipal = call.principal() ?: throw IllegalStateException("Principal ikke satt.")
        return Eier(jwtPrincipal.payload.subject)
    }

    private fun eierFraQuery(call: ApplicationCall) : Eier {
        logger.trace("Ser om det er en query parameter 'eier' som skal brukes")
        val eierId = call.request.queryParameters["eier"] ?: throw IllegalStateException("'eier' query parameter ikke satt.")
        return Eier(eierId)
    }
}