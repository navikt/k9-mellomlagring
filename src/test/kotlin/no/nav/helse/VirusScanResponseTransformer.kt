package no.nav.helse

import com.github.tomakehurst.wiremock.common.FileSource
import com.github.tomakehurst.wiremock.extension.Parameters
import com.github.tomakehurst.wiremock.extension.ResponseTransformer
import com.github.tomakehurst.wiremock.http.Request
import com.github.tomakehurst.wiremock.http.Response
import no.nav.helse.dusseldorf.ktor.core.fromResources

class VirsScanResponseTransformer : ResponseTransformer() {
    private companion object {
        private val infectedDocument = "iPhone_6_infected.jpg".fromResources().readBytes()
    }
    override fun transform(
        request: Request?,
        response: Response?,
        files: FileSource?,
        parameters: Parameters?
    ): Response {
        val document = request!!.body
        return if (infectedDocument.contentEquals(document)) {
            Response.Builder.like(response)
                .body(getResponse(result = "Infected"))
                .build()
        } else {
            Response.Builder.like(response)
                .body(getResponse(result = "OK"))
                .build()
        }
    }

    override fun getName(): String {
        return "virus-scan"
    }

    override fun applyGlobally(): Boolean {
        return false
    }

}

private fun getResponse(
    result: String
) = """
[{
    "Result": "$result"
}]
""".trimIndent()