package no.nav.helse.dokument.eier

import no.nav.helse.dusseldorf.ktor.auth.IdToken
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class EierResolver {
    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(EierResolver::class.java)
    }

    internal fun hentEier(idToken: IdToken, eiersFødselsnummer: String): Eier {
        return when {
            idToken.issuerIsAzure() -> Eier(eiersFødselsnummer)
            idToken.issuerIsTokendings() -> hentEierFraClaim(idToken, eiersFødselsnummer)
            else -> throw IllegalArgumentException("Ikke støttet issuer ${idToken.issuer()}")
        }
    }

    private fun hentEierFraClaim(idToken: IdToken, eiersFødselsnummer: String): Eier {
        logger.trace("Forsøker å hente eier fra JWT token 'sub' claim")
        val norskIdentifikasjonsnummer = idToken.getNorskIdentifikasjonsnummer()
        if (norskIdentifikasjonsnummer != eiersFødselsnummer) throw IllegalArgumentException("Eiers token samsvarer ikke med forespurt eier.")
        return Eier(norskIdentifikasjonsnummer)
    }
}
