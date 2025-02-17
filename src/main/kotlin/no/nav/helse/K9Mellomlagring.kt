package no.nav.helse

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import io.ktor.http.HttpHeaders
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.routing.RoutingRoot
import io.prometheus.client.hotspot.DefaultExports
import no.nav.helse.dokument.DokumentService
import no.nav.helse.dokument.VirusScanner
import no.nav.helse.dokument.api.ContentTypeService
import no.nav.helse.dokument.api.dokumentV1Apis
import no.nav.helse.dokument.crypto.Cryptography
import no.nav.helse.dokument.eier.EierResolver
import no.nav.helse.dokument.storage.Storage
import no.nav.helse.dusseldorf.ktor.auth.AuthStatusPages
import no.nav.helse.dusseldorf.ktor.auth.idToken
import no.nav.helse.dusseldorf.ktor.core.CallIdRequired
import no.nav.helse.dusseldorf.ktor.core.DefaultProbeRoutes
import no.nav.helse.dusseldorf.ktor.core.DefaultStatusPages
import no.nav.helse.dusseldorf.ktor.core.correlationIdAndRequestIdInMdc
import no.nav.helse.dusseldorf.ktor.core.fromXCorrelationIdHeader
import no.nav.helse.dusseldorf.ktor.core.id
import no.nav.helse.dusseldorf.ktor.core.log
import no.nav.helse.dusseldorf.ktor.core.logProxyProperties
import no.nav.helse.dusseldorf.ktor.core.logRequests
import no.nav.helse.dusseldorf.ktor.core.requiresCallId
import no.nav.helse.dusseldorf.ktor.health.HealthCheck
import no.nav.helse.dusseldorf.ktor.health.HealthRoute
import no.nav.helse.dusseldorf.ktor.health.HealthService
import no.nav.helse.dusseldorf.ktor.health.Healthy
import no.nav.helse.dusseldorf.ktor.health.Result
import no.nav.helse.dusseldorf.ktor.health.UnHealthy
import no.nav.helse.dusseldorf.ktor.jackson.JacksonStatusPages
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.helse.dusseldorf.ktor.metrics.MetricsRoute
import no.nav.helse.dusseldorf.ktor.metrics.init
import no.nav.security.token.support.v3.RequiredClaims
import no.nav.security.token.support.v3.asIssuerProps
import no.nav.security.token.support.v3.tokenValidationSupport
import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.k9Mellomlagring() {
    val applicationConfig = environment.config
    val appId = applicationConfig.id()
    logProxyProperties()
    DefaultExports.initialize()
    val logger = LoggerFactory.getLogger("no.nav.k9.k9Mellomlagring")

    val allIssuers = applicationConfig.asIssuerProps().keys
    val configuration = Configuration(applicationConfig)

    install(Authentication) {
        allIssuers
            .filterNot { it == "azure" }
            .forEach { issuer: String ->
                tokenValidationSupport(
                    name = issuer,
                    config = applicationConfig,
                    requiredClaims = RequiredClaims(
                        issuer = issuer,
                        claimMap = arrayOf("acr=Level4")
                    )
                )
            }

        allIssuers
            .filter { it == "azure" }
            .forEach { issuer: String ->
                tokenValidationSupport(
                    name = issuer,
                    config = applicationConfig,
                    requiredClaims = RequiredClaims(
                        issuer = issuer,
                        claimMap = arrayOf("roles=access_as_application")
                    )
                )
            }
    }

    install(ContentNegotiation) {
        jackson {
            k9MellomlagringConfigured()
        }
    }

    install(StatusPages) {
        DefaultStatusPages()
        JacksonStatusPages()
        AuthStatusPages()
    }

    val storage = configuration.getStorageConfigured()

    install(CallIdRequired)

    val virusScanner: VirusScanner? = getVirusScanner(configuration)
    val dokumentService = DokumentService(
        cryptography = Cryptography(
            encryptionPassphrase = configuration.getEncryptionPassphrase(),
            decryptionPassphrases = configuration.getDecryptionPassphrases()
        ),
        storage = storage,
        virusScanner = virusScanner
    )

    val eierResolver = EierResolver()

    val contentTypeService = ContentTypeService()

    install(RoutingRoot) {
        authenticate(*allIssuers.toTypedArray()) {
            requiresCallId {
                dokumentV1Apis(
                    dokumentService = dokumentService,
                    eierResolver = eierResolver,
                    contentTypeService = contentTypeService,
                    baseUrl = configuration.getBaseUrl()
                )
            }
        }

        DefaultProbeRoutes()
        MetricsRoute()
        val healthChecks: MutableSet<HealthCheck> = mutableSetOf(
            StorageHealthCheck(
                storage = storage
            )
        )
        virusScanner?.let { healthChecks.add(it) }
        HealthRoute(healthService = HealthService(healthChecks))
    }

    install(MicrometerMetrics) {
        //TODO: Fiks før prodsetting. Feiler med Failed to register Collector of type MicrometerCollector: ktor_http_server_requests_active is already in use by another Collector of type MicrometerCollector
        //init(appId)
    }

    install(CallId) {
        //fromXCorrelationIdHeader()
        retrieveFromHeader(HttpHeaders.XCorrelationId)
    }

    intercept(ApplicationCallPipeline.Monitoring) {
        call.request.log()
    }

    install(CallLogging) {
        correlationIdAndRequestIdInMdc()
        logRequests()
        mdc("id_token_jti") { call ->
            try {
                val idToken = call.idToken()
                logger.info("Issuer [{}]", idToken.issuer())
                idToken.getId()
            } catch (cause: Throwable) {
                null
            }
        }
    }
}

private fun getVirusScanner(config: Configuration): VirusScanner? {
    if (!config.enableVirusScan()) return null
    return VirusScanner(url = config.getVirusScanUrl())
}

internal fun ObjectMapper.k9MellomlagringConfigured() = dusseldorfConfigured()
    .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

class StorageHealthCheck(
    private val storage: Storage
) : HealthCheck {

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(StorageHealthCheck::class.java)
        private val name = "StorageHealthCheck"
    }

    override suspend fun check(): Result {
        return try {
            storage.ready()
            Healthy(name = name, result = "Tilkobling mot storage OK.")
        } catch (cause: Throwable) {
            logger.error("Feil ved tilkobling mot storage.", cause)
            UnHealthy(name = name, result = cause.message ?: "Feil ved tilkobling mot storage.")
        }
    }
}
