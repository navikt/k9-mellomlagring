package no.nav.helse

import io.ktor.server.testing.*
import no.nav.helse.dusseldorf.testsupport.asArguments
import no.nav.helse.dusseldorf.testsupport.wiremock.WireMockBuilder
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class K9MellomlagringWithMocks {
    companion object {

        private val logger: Logger = LoggerFactory.getLogger(K9MellomlagringWithMocks::class.java)

        @JvmStatic
        fun main(args: Array<String>) {
            val mockOAuth2Server = MockOAuth2Server()
            val wireMockServer = WireMockBuilder()
                .withPort(8131)
                .withAzureSupport()
                .k9MellomlagringConfiguration()
                .build()
                .stubVirusScan()

            val testArgs = TestConfiguration.asMap(
                wireMockServer = wireMockServer,
                port = 8132,
                mockOAuth2Server = mockOAuth2Server
            ).asArguments()

            Runtime.getRuntime().addShutdownHook(object : Thread() {
                override fun run() {
                    logger.info("Tearing down")
                    wireMockServer.stop()
                    logger.info("Tear down complete")
                }
            })

            testApplication { no.nav.helse.main(testArgs) }
        }
    }
}
