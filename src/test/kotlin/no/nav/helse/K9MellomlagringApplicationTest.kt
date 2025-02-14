package no.nav.helse

import com.typesafe.config.ConfigFactory
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.testing.testApplication
import io.prometheus.client.CollectorRegistry
import no.nav.helse.dusseldorf.ktor.core.fromResources
import no.nav.helse.dusseldorf.testsupport.wiremock.WireMockBuilder
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.json.JSONObject
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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

    @ParameterizedTest
    @ValueSource(strings = ["/isready", "/isalive", "/metrics", "/health"])
    fun `test isready, isalive, health og metrics`(endpoint: String) = testApplication {
        environment {
            config = getConfig()
        }

        client.get(endpoint) {
            headers {
                append(HttpHeaders.XCorrelationId, UUID.randomUUID().toString())
            }
        }.also {
            assertEquals(HttpStatusCode.OK, it.status)
        }
    }


    @Test
    fun `Lagre dokument med brukertoken`() = testApplication {
        environment {
            config = getConfig()
        }

        client.post("/v1/dokument") {
            headers {
                append(HttpHeaders.XCorrelationId, UUID.randomUUID().toString())
                append(HttpHeaders.Accept, "application/json")
                append(HttpHeaders.ContentType, "application/json")
                append(HttpHeaders.Authorization, "Bearer $tokenXToken")
            }
            setBody(vedlegg)
        }.apply {
            assertEquals(HttpStatusCode.Created, status)
        }
    }

    @Test
    fun `Persistere dokument med brukertoken skal ikke gå`() = testApplication {
        environment {
            config = getConfig()
        }

        client.put("/v1/dokument/123/persister") {
            headers {
                append(HttpHeaders.XCorrelationId, UUID.randomUUID().toString())
                append(HttpHeaders.Accept, "application/json")
                append(HttpHeaders.Authorization, "Bearer $tokenXToken")
            }
        }.apply {
            assertEquals(HttpStatusCode.Forbidden, status)
        }
    }

    @Test
    fun `Persistere dokument med systemtoken skal gå`() = testApplication {
        environment {
            config = getConfig()
        }

        var dokumentId: String
        client.post("/v1/dokument") {
            headers {
                append(HttpHeaders.XCorrelationId, UUID.randomUUID().toString())
                append(HttpHeaders.Accept, "application/json")
                append(HttpHeaders.ContentType, "application/json")
                append(HttpHeaders.Authorization, "Bearer $tokenXToken")
            }
            setBody(vedlegg)
        }.apply {
            assertEquals(HttpStatusCode.Created, status)
            dokumentId = JSONObject(bodyAsText()).getString("id")
        }

        client.put("/v1/dokument/$dokumentId/persister") {
            headers {
                append(HttpHeaders.XCorrelationId, UUID.randomUUID().toString())
                append(HttpHeaders.Accept, "application/json")
                append(HttpHeaders.Authorization, "Bearer $azureToken")
                append(HttpHeaders.ContentType, "application/json")
            }
            setBody(
                """
                    {
                        "eiers_fødselsnummer" : "02119970078"
                    }
                """.trimIndent()
            )
        }.apply {
            assertEquals(HttpStatusCode.NoContent, status)
        }
    }
}
