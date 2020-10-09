package no.nav.helse

import com.auth0.jwt.JWT
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.http.content.PartData
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.utils.io.streams.asInput
import no.nav.helse.dusseldorf.ktor.core.fromResources
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val objectMapper = jacksonObjectMapper().k9MellomlagringConfigured()

fun TestApplicationEngine.lasteOppDokumentMultipart(
    token: String,
    fileName: String = "iPhone_6.jpg",
    fileContent: ByteArray = fileName.fromResources().readBytes(),
    tittel: String = "En eller annen tittel",
    contentType: String = if (fileName.endsWith("pdf")) "application/pdf" else "image/jpeg",
    expectedHttpStatusCode : HttpStatusCode = HttpStatusCode.Created,
    eier: String? = null
) : String {

    val boundary = "***dokument***"

    val path = if (eier == null) "/v1/dokument" else "/v1/dokument?eier=$eier"

    handleRequest(HttpMethod.Post, path) {
        addHeader(HttpHeaders.Authorization, "Bearer $token")
        addHeader(HttpHeaders.XCorrelationId, "laster-opp-doument-ok-multipart")
        addHeader(
            HttpHeaders.ContentType,
            ContentType.MultiPart.FormData.withParameter("boundary", boundary).toString()
        )
        setBody(
            boundary,
            listOf(
                PartData.FileItem(
                    { fileContent.inputStream().asInput() }, {},
                    headersOf(
                        Pair(
                            HttpHeaders.ContentType,
                            listOf(contentType)
                        ),
                        Pair(
                            HttpHeaders.ContentDisposition,
                            listOf(
                                ContentDisposition.File
                                    .withParameter(ContentDisposition.Parameters.Name, "content")
                                    .withParameter(ContentDisposition.Parameters.FileName, fileName)
                                    .toString()
                            )
                        )
                    )
                ),
                PartData.FormItem(
                    tittel, {},
                    headersOf(
                        HttpHeaders.ContentDisposition,
                        listOf(
                            ContentDisposition.Inline
                                .withParameter(ContentDisposition.Parameters.Name, "title")
                                .toString()
                        )
                    )
                )
            )
        )
    }.apply {
        assertEquals(expectedHttpStatusCode, response.status())
        return if(expectedHttpStatusCode == HttpStatusCode.Created) {
            testDokumentIdFormat(response.content)
            assertResponseAndGetLocationHeader()
        } else ""
    }
}


fun TestApplicationEngine.lasteOppDokumentJson(
    token: String,
    fileName: String = "iPhone_6.jpg",
    fileContent: ByteArray = fileName.fromResources().readBytes(),
    tittel: String = "En eller annen tittel",
    contentType: String = if (fileName.endsWith("pdf")) "application/pdf" else "image/jpeg",
    eier : String? = null,
    expectedHttpStatusCode : HttpStatusCode = HttpStatusCode.Created
) : String {
    val base64encodedContent = Base64.getEncoder().encodeToString(fileContent)

    val path = if (eier == null) "/v1/dokument" else "/v1/dokument?eier=$eier"

    handleRequest(HttpMethod.Post, path) {
        addHeader(HttpHeaders.Authorization, "Bearer $token")
        addHeader(HttpHeaders.ContentType, "application/json")
        addHeader(HttpHeaders.XCorrelationId, "laster-opp-doument-ok-json")
        setBody("""
            {
                "content" : "$base64encodedContent",
                "content_type": "$contentType",
                "title" : "$tittel"
            }
            """.trimIndent()
        ) }.apply {
            assertEquals(expectedHttpStatusCode, response.status())
            return if(expectedHttpStatusCode == HttpStatusCode.Created) {
                testDokumentIdFormat(response.content)
                assertResponseAndGetLocationHeader()
            } else ""
        }
}

private fun testDokumentIdFormat(responseEntity: String?) {
    assertNotNull(responseEntity)
    val tree = objectMapper.readTree(responseEntity)
    val id = tree.get("id").asText() + "."
    val decodedId = JWT.decode(id)
    assertNotNull(decodedId.getHeaderClaim("kid").asString())
    assertEquals(decodedId.getHeaderClaim("typ").asString(), "JWT")
    assertEquals(decodedId.getHeaderClaim("alg").asString(), "none")
    assertNotNull(decodedId.getClaim("jti").asString())
}

private fun TestApplicationCall.assertResponseAndGetLocationHeader() : String {
    assertNotNull(response.byteContent)
    val entity : Map<String, String> = objectMapper.readValue(response.byteContent!!)
    assertNotNull(entity)
    assertTrue(entity.containsKey("id"))
    assertNotNull(entity["id"])
    val locationHeader = response.headers[HttpHeaders.Location]
    assertNotNull(locationHeader)
    assertEquals(locationHeader.substringAfterLast("/"), entity["id"])
    return locationHeader
}
