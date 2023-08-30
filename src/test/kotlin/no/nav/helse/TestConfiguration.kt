package no.nav.helse

import com.github.tomakehurst.wiremock.WireMockServer
import no.nav.security.mock.oauth2.MockOAuth2Server

internal object TestConfiguration {

    internal fun asMap(
        wireMockServer: WireMockServer? = null,
        port: Int = 8080,
        virusScanUrl: String? = wireMockServer?.getVirusScanUrl(),
        passphrase1: String = "password",
        passphrase2: String = "oldpassword",
        passphrase3: String = "reallyoldpassword",
        mockOAuth2Server: MockOAuth2Server
    ): Map<String, String> {
        val map = mutableMapOf(
            Pair("ktor.deployment.port", "$port"),
            Pair("nav.crypto.passphrase.encryption_identifier", "1"),
            Pair("nav.crypto.passphrase.decryption_identifiers", "2,3"),
            Pair("CRYPTO_PASSPHRASE_1", passphrase1),
            Pair("CRYPTO_PASSPHRASE_2", passphrase2),
            Pair("CRYPTO_PASSPHRASE_3", passphrase3),
            Pair("nav.virus_scan.url", "$virusScanUrl"),
            Pair("nav.base_url", "http://localhost:$port")
        )

        // Issuers
        if (wireMockServer != null) {
            map["no.nav.security.jwt.issuers.0.issuer_name"] = "tokendings"
            map["no.nav.security.jwt.issuers.0.discoveryurl"] = "${mockOAuth2Server.wellKnownUrl("tokendings")}"
            map["no.nav.security.jwt.issuers.0.accepted_audience"] = "dev-gcp:dusseldorf:k9-mellomlagring"

            map["no.nav.security.jwt.issuers.1.issuer_name"] = "azure"
            map["no.nav.security.jwt.issuers.1.discoveryurl"] = "${mockOAuth2Server.wellKnownUrl("azure")}"
            map["no.nav.security.jwt.issuers.1.accepted_audience"] = "dev-gcp:dusseldorf:k9-mellomlagring"
        }

        map["nav.local_or_test"] = "true"

        return map.toMap()
    }
}
