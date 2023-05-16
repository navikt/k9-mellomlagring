package no.nav.helse.dokument

import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.github.kittinunf.fuel.httpPut
import io.prometheus.client.Counter
import no.nav.helse.dusseldorf.ktor.health.HealthCheck
import no.nav.helse.dusseldorf.ktor.health.Healthy
import no.nav.helse.dusseldorf.ktor.health.Result
import no.nav.helse.dusseldorf.ktor.health.UnHealthy
import no.nav.helse.dusseldorf.ktor.metrics.Operation
import org.json.JSONArray
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.net.URI
import java.time.Duration

class VirusScanner(
    url: URI
): HealthCheck {
    private companion object {
        private val logger: Logger = LoggerFactory.getLogger("nav.VirusScanner")
        private val virusScannerCounter = Counter.build()
            .name("virus_scan_counter")
            .help("Teller for scanning for virus i dokumenter")
            .labelNames("result")
            .register()
    }

    private val gateway = ClamAvGateway(url)

    suspend fun scan(dokument: Dokument, helsesjekk: Boolean = false): ScanResult {
        logger.info("Scanner Dokument for virus.")
        val scanResult = gateway.scan(dokument)
        logger.info("scanResult=$scanResult")
        if (helsesjekk.not()) virusScannerCounter.labels(scanResult.name).inc()
        if (ScanResult.INFECTED == scanResult) {
            throw IllegalStateException("Dokumentet inneholder virus.")
        }
        // CLEAN/SCAN_ERROR håndteres som OK
        return scanResult
    }

    override suspend fun check(): Result {
        val result = scan(
            dokument = Dokument(
                title = "",
                eier = DokumentEier(eiersFødselsnummer = ""),
                content = ByteArray(0),
                contentType = ""
            ),
            helsesjekk = true
        )

        return when(result) {
            ScanResult.SCAN_ERROR -> {
                logger.error("ClamAvGateway er ikke tilgjengelig...")
                UnHealthy("ClamAvGateway", "Feil ved helsesjekk av ClamAvGateway.")
            }
            else -> Healthy("ClamAvGateway", "OK -> $result")
        }
    }
}

enum class ScanResult {
    CLEAN,
    INFECTED,
    SCAN_ERROR
}


private class ClamAvGateway(
    url: URI
) {
    private companion object {
        private val logger = LoggerFactory.getLogger("nav.ClamAvGateway")
        private val timeout = Duration.ofSeconds(10).toMillisPart()
        private val headers = mapOf(
            Headers.ACCEPT to "application/json",
            Headers.CONTENT_TYPE to "text/plain"
        )
    }

    private val urlString = url.toString()
    internal suspend fun scan(dokument: Dokument) : ScanResult {
        val contentStream = { ByteArrayInputStream(dokument.content) }

        val (_, _, res) = Operation.monitored(
            app = "k9-mellomlagring",
            operation = "scanne-dokument-for-virus",
            resultResolver = { 200 == it.second.statusCode}
        ) {
            urlString
                .httpPut()
                .body(contentStream)
                .timeout(timeout)
                .header(headers)
                .awaitStringResponseResult()
        }
        return res.fold(
            { success ->
                try {
                    JSONArray(success)
                        .getJSONObject(0)
                        .getString("Result")
                        .tilScanResult()
                } catch (cause: Throwable) {
                    logger.error("Response fra virusscan ikke JSON. Response = '$success'", cause)
                    ScanResult.SCAN_ERROR
                }

            },
            { error ->
                logger.error("Feil ved virusscan. $error")
                ScanResult.SCAN_ERROR
            }
        )
    }
}

private fun String.tilScanResult(): ScanResult {
    return if (uppercase() == "OK") ScanResult.CLEAN else ScanResult.INFECTED
}
