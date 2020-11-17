package no.nav.helse

import com.github.tomakehurst.wiremock.WireMockServer
import no.nav.helse.dusseldorf.testsupport.wiremock.getAzureV1WellKnownUrl
import no.nav.helse.dusseldorf.testsupport.wiremock.getAzureV2WellKnownUrl
import no.nav.helse.dusseldorf.testsupport.wiremock.getLoginServiceV1WellKnownUrl

internal object TestConfiguration {

    internal fun asMap(
        wireMockServer: WireMockServer? = null,
        port: Int = 8080,
        virusScanUrl: String? = wireMockServer?.getVirusScanUrl(),
        passphrase1: String = "password",
        passphrase2: String = "oldpassword",
        passphrase3: String = "reallyoldpassword",
        k9MellomlagringAzureClientId: String = "k9-mellomlagring"
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
            map["nav.auth.issuers.0.alias"] = "login-service-v1"
            map["nav.auth.issuers.0.discovery_endpoint"] = wireMockServer.getLoginServiceV1WellKnownUrl()

            map["nav.auth.issuers.2.alias"] = "login-service-v2"
            map["nav.auth.issuers.2.discovery_endpoint"] = wireMockServer.getLoginServiceV1WellKnownUrl()

            map["nav.auth.issuers.2.type"] = "azure"
            map["nav.auth.issuers.2.alias"] = "azure-v1"
            map["nav.auth.issuers.2.discovery_endpoint"] = wireMockServer.getAzureV1WellKnownUrl()
            map["nav.auth.issuers.2.audience"] = k9MellomlagringAzureClientId
            map["nav.auth.issuers.2.azure.require_certificate_client_authentication"] = "false"
            map["nav.auth.issuers.2.azure.required_roles"] = "access_as_application"

            map["nav.auth.issuers.3.type"] = "azure"
            map["nav.auth.issuers.3.alias"] = "azure-v2"
            map["nav.auth.issuers.3.discovery_endpoint"] = wireMockServer.getAzureV2WellKnownUrl()
            map["nav.auth.issuers.3.audience"] = k9MellomlagringAzureClientId
            map["nav.auth.issuers.3.azure.require_certificate_client_authentication"] = "false"
            map["nav.auth.issuers.3.azure.required_roles"] = "access_as_application"
        }

        map["nav.local_or_test"] = "true"

        return map.toMap()
    }
}
