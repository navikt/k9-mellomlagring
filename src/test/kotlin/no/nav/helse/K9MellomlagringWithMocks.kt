package no.nav.helse

import io.ktor.server.testing.withApplication
import no.nav.helse.dusseldorf.testsupport.asArguments
import no.nav.helse.dusseldorf.testsupport.wiremock.WireMockBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class K9MellomlagringWithMocks {
    companion object {

        private val logger: Logger = LoggerFactory.getLogger(K9MellomlagringWithMocks::class.java)

        @JvmStatic
        fun main(args: Array<String>) {

            val wireMockServer = WireMockBuilder()
                .withPort(8131)
                .withLoginServiceSupport()
                .withAzureSupport()
                .k9MellomlagringConfiguration()
                .build()
                .stubVirusScan()

            val testArgs = TestConfiguration.asMap(
                wireMockServer = wireMockServer,
                port = 8132
            ).asArguments()

            Runtime.getRuntime().addShutdownHook(object : Thread() {
                override fun run() {
                    logger.info("Tearing down")
                    wireMockServer.stop()
                    logger.info("Tear down complete")
                }
            })

            withApplication { no.nav.helse.main(testArgs) }
        }
    }
}
