package no.nav.helse

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import no.nav.helse.dusseldorf.testsupport.wiremock.WireMockBuilder

private const val virusScanPath = "/virus-scan-mock/scan"

internal fun WireMockBuilder.k9MellomlagringConfiguration(): WireMockBuilder {
    wireMockConfiguration {
        it.extensions(VirsScanResponseTransformer())
    }
    return this
}

internal fun WireMockServer.stubVirusScan() : WireMockServer {
    WireMock.stubFor(
        WireMock.put(WireMock.urlPathMatching(".*$virusScanPath.*"))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withStatus(200)
                    .withTransformers("virus-scan")
            )
    )
    return this
}

fun WireMockServer.getVirusScanUrl() = baseUrl() + virusScanPath
