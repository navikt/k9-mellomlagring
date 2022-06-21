package no.nav.helse

import com.typesafe.config.ConfigFactory
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import io.prometheus.client.CollectorRegistry
import no.nav.helse.dusseldorf.ktor.core.fromResources
import no.nav.helse.dusseldorf.testsupport.wiremock.WireMockBuilder
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.json.JSONObject
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class K9MellomlagringApplicationTest {

    private companion object {

        private val logger: Logger = LoggerFactory.getLogger(K9MellomlagringApplicationTest::class.java)
        val mockOAuth2Server = MockOAuth2Server().apply { start() }
        val wireMockServer = WireMockBuilder()
            .withAzureSupport()
            .withNaisStsSupport()
            .withLoginServiceSupport()
            .withTokendingsSupport()
            .build()
            .stubVirusScan()

        private val tokenXToken = mockOAuth2Server.issueToken(
            issuerId = "tokendings",
            audience = "dev-gcp:dusseldorf:k9-mellomlagring",
            claims = mapOf("acr" to "Level4"),
            subject = "02119970078"
        ).serialize()

        private val azureToken = mockOAuth2Server.issueToken(
            issuerId = "azure",
            audience = "dev-gcp:dusseldorf:k9-mellomlagring",
            claims = mapOf("roles" to "access_as_application")
        ).serialize()

        fun getConfig(): ApplicationConfig {
            val fileConfig = ConfigFactory.load()
            val testConfig = ConfigFactory.parseMap(
                TestConfiguration.asMap(
                    wireMockServer = wireMockServer,
                    mockOAuth2Server = mockOAuth2Server
                )
            )
            val mergedConfig = testConfig.withFallback(fileConfig)

            return HoconApplicationConfig(mergedConfig)
        }

        val engine = TestApplicationEngine(createTestEnvironment { config = getConfig() })

        val iPhoneFil = Base64.getEncoder().encodeToString("iPhone_6.jpg".fromResources().readBytes())
        val vedlegg = """
            {
                "content" : "$iPhoneFil",
                "content_type": "image/jpeg",
                "title" : "iPhone_6.jpeg",
                "eier" : {
                    "eiers_fødselsnummer" : "02119970078"
                }
            }
        """.trimIndent()

        @BeforeAll
        @JvmStatic
        fun buildUp() {
            CollectorRegistry.defaultRegistry.clear()
            engine.start(wait = true)
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            logger.info("Tearing down")
            wireMockServer.stop()
            mockOAuth2Server.shutdown()
            logger.info("Tear down complete")
        }
    }

    @Test
    fun `test isready, isalive, health og metrics`() {
        with(engine) {
            handleRequest(HttpMethod.Get, "/isready") {}.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                handleRequest(HttpMethod.Get, "/isalive") {}.apply {
                    assertEquals(HttpStatusCode.OK, response.status())
                    handleRequest(HttpMethod.Get, "/metrics") {}.apply {
                        assertEquals(HttpStatusCode.OK, response.status())
                        handleRequest(HttpMethod.Get, "/health") {}.apply {
                            assertEquals(HttpStatusCode.OK, response.status())
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Lagre dokument med brukertoken`(){
        with(engine){
            handleRequest(HttpMethod.Post, "/v1/dokument" ){
                addHeader(HttpHeaders.XCorrelationId, UUID.randomUUID().toString())
                addHeader(HttpHeaders.Accept, "application/json")
                addHeader(HttpHeaders.ContentType, "application/json")
                addHeader(HttpHeaders.Authorization, "Bearer $tokenXToken")
                setBody(vedlegg)
            }.apply {
                assertEquals(HttpStatusCode.Created, response.status())
            }
        }
    }

    @Test
    fun `Persistere dokument med brukertoken skal ikke gå`(){
        with(engine){
            handleRequest(HttpMethod.Put, "/v1/dokument/123/persister" ){
                addHeader(HttpHeaders.XCorrelationId, UUID.randomUUID().toString())
                addHeader(HttpHeaders.Accept, "application/json")
                addHeader(HttpHeaders.Authorization, "Bearer $tokenXToken")
            }.apply {
                assertEquals(HttpStatusCode.Forbidden, response.status())
            }
        }
    }

    @Test
    fun `Persistere dokument med systemtoken skal gå`(){
        with(engine){
            var dokumentId: String
            handleRequest(HttpMethod.Post, "/v1/dokument" ){
                addHeader(HttpHeaders.XCorrelationId, UUID.randomUUID().toString())
                addHeader(HttpHeaders.Accept, "application/json")
                addHeader(HttpHeaders.ContentType, "application/json")
                addHeader(HttpHeaders.Authorization, "Bearer $tokenXToken")
                setBody(vedlegg)
            }.apply {
                assertEquals(HttpStatusCode.Created, response.status())
                dokumentId = JSONObject(response.content.toString()).getString("id")
            }

            handleRequest(HttpMethod.Put, "/v1/dokument/$dokumentId/persister" ){
                addHeader(HttpHeaders.XCorrelationId, UUID.randomUUID().toString())
                addHeader(HttpHeaders.Accept, "application/json")
                addHeader(HttpHeaders.Authorization, "Bearer $azureToken")
                addHeader(HttpHeaders.ContentType, "application/json")
                setBody("""
                    {
                        "eiers_fødselsnummer" : "02119970078" 
                    }
                """.trimIndent())
            }.apply {
                assertEquals(HttpStatusCode.NoContent, response.status())
            }
        }
    }
}
