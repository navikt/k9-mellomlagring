package no.nav.helse.dokument.eier

import io.ktor.auth.jwt.JWTPrincipal
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

    internal fun hentEier(principal: JWTPrincipal, eiersFødselsnummer: String): Eier {
        return when (val issuer = principal.payload.issuer) {
            azureV1Issuer, azureV2Issuer -> Eier(eiersFødselsnummer)
            loginServiceV1Issuer -> hentEierFraClaim(principal, eiersFødselsnummer)
            else -> throw IllegalArgumentException("Ikke støttet issuer $issuer")
        }
    }

    private fun hentEierFraClaim(principal: JWTPrincipal, eiersFødselsnummer: String): Eier {
        logger.trace("Forsøker å hente eier fra JWT token 'sub' claim")
        val subject = principal.payload.subject
        if (subject != eiersFødselsnummer) throw IllegalArgumentException("Eiers token samsvarer ikke med forespurt eier.")
        return Eier(subject)
    }
}
