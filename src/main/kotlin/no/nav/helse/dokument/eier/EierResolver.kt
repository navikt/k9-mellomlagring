package no.nav.helse.dokument.eier

import io.ktor.application.ApplicationCall
import io.ktor.auth.*
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.request.*
import no.nav.helse.dusseldorf.ktor.auth.ClaimRule
import no.nav.helse.dusseldorf.ktor.auth.Issuer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class EierResolver(
    issuers: Map<Issuer, Set<ClaimRule>>
) {
    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(EierResolver::class.java)
    }

    private val azureV1Issuer = issuers.filterKeys { it.alias() == "azure-v1" }.entries.first().key.issuer()
    private val azureV2Issuer = issuers.filterKeys { it.alias() == "azure-v2" }.entries.first().key.issuer()
    private val loginServiceV1Issuer =
        issuers.filterKeys { it.alias() == "login-service-v1" }.entries.first().key.issuer()

    internal suspend fun hentEier(call: ApplicationCall): Eier {
        val principal: JWTPrincipal = call.principal() ?: throw IllegalStateException("Principal ikke satt.")

        return when (val issuer = principal.payload.issuer) {
            azureV1Issuer, azureV2Issuer -> hentEierFraEntity(call)
            loginServiceV1Issuer -> hentEierFraClaim(principal)
            else -> throw IllegalArgumentException("Ikke støttet issuer $issuer")
        }
    }

    private fun hentEierFraClaim(principal: JWTPrincipal): Eier {
        logger.trace("Forsøker å hente eier fra JWT token 'sub' claim")
        return Eier(principal.payload.subject)
    }

    private suspend fun hentEierFraEntity(call: ApplicationCall): Eier {
        logger.trace("Ser om det er en entity parameter 'eier' som skal brukes")
        val dokumentEier: DokumentEier = call.receive()
        return Eier(dokumentEier.identitetsnummer)
    }
}

data class DokumentEier(
    val identitetsnummer: String
)
