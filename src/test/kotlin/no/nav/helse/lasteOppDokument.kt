package no.nav.helse

import com.auth0.jwt.JWT
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.headersOf
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.util.cio.KtorDefaultPool
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import no.nav.helse.dusseldorf.ktor.core.fromResources
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val objectMapper = jacksonObjectMapper().k9MellomlagringConfigured()

suspend fun TestApplicationEngine.lasteOppDokumentMultipart(
    token: String,
    fileName: String = "iPhone_6.jpg",
    fileContent: ByteArray = fileName.fromResources().readBytes(),
    tittel: String = "En eller annen tittel",
    contentType: String = if (fileName.endsWith("pdf")) "application/pdf" else "image/jpeg",
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
    eier: String? = null,
): String {

    val boundary = "***dokument***"

    val path = if (eier == null) "/v1/dokument" else "/v1/dokument?eier=$eier"

    client.post(path) {
        headers {
            append(HttpHeaders.Authorization, "Bearer $token")
            append(HttpHeaders.XCorrelationId, "laster-opp-doument-ok-multipart")
            append(
                HttpHeaders.ContentType,
                ContentType.MultiPart.FormData.withParameter("boundary", boundary).toString()
            )
        }

        listOf(
            PartData.FileItem(
                { fileContent.inputStream().toByteReadChannel(Dispatchers.IO + Job(), KtorDefaultPool) }, {},
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

        setBody(
            boundary,

            )
    }.apply {
        assertEquals(expectedHttpStatusCode, status)
        return if (expectedHttpStatusCode == HttpStatusCode.Created) {
            testDokumentIdFormat(body())
            assertResponseAndGetLocationHeader()
        } else ""
    }
}


suspend fun TestApplicationEngine.lasteOppDokumentJson(
    token: String,
    fileName: String = "iPhone_6.jpg",
    fileContent: ByteArray = fileName.fromResources().readBytes(),
    tittel: String = "En eller annen tittel",
    contentType: String = if (fileName.endsWith("pdf")) "application/pdf" else "image/jpeg",
    eier: String? = null,
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
): String {
    val base64encodedContent = Base64.getEncoder().encodeToString(fileContent)

    val path = if (eier == null) "/v1/dokument" else "/v1/dokument?eier=$eier"

    client.post(path) {
        headers {
            append(HttpHeaders.Authorization, "Bearer $token")
            append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            append(HttpHeaders.XCorrelationId, "laster-opp-doument-ok-json")
        }

        setBody(
            """
            {
                "content" : "$base64encodedContent",
                "content_type": "$contentType",
                "title" : "$tittel"
            }
            """.trimIndent()
        )
    }.apply {
        assertEquals(expectedHttpStatusCode, status)
        return if (expectedHttpStatusCode == HttpStatusCode.Created) {
            testDokumentIdFormat(body())
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

private suspend fun HttpResponse.assertResponseAndGetLocationHeader(): String {
    val bodyAsBytes = bodyAsBytes()
    assertNotNull(bodyAsBytes)
    val entity: Map<String, String> = objectMapper.readValue(bodyAsBytes)
    assertNotNull(entity)
    assertTrue(entity.containsKey("id"))
    assertNotNull(entity["id"])
    val locationHeader = headers[HttpHeaders.Location]
    assertNotNull(locationHeader)
    assertEquals(locationHeader.substringAfterLast("/"), entity["id"])
    return locationHeader
}
