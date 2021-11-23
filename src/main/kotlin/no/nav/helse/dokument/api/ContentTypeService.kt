package no.nav.helse.dokument.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.*
import no.nav.helse.k9MellomlagringConfigured
import org.apache.tika.Tika
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger("nav.ContentTypeService")

class ContentTypeService {
    companion object {
        val JSON = ContentType.Application.Json
        val PDF = ContentType.parse("application/pdf")
        val XML = ContentType.Application.Xml
        val PNG = ContentType.Image.PNG
        val JPEG = ContentType.Image.JPEG
        val PLAIN_TEXT = ContentType.Text.Plain
    }

    private val tika = Tika()
    private val objectMapper = jacksonObjectMapper().k9MellomlagringConfigured()
    private val supportedContentTypes = listOf(JSON, PDF, XML, PNG, JPEG)

    internal fun getContentType(
        contentType: String,
        content: ByteArray
    ) : ContentType {
        val parsedContentType = ContentType.parseOrNull(contentType) ?: throw IllegalArgumentException("Klarte ikke å parse ContentType=$contentType")
        val isWhatItSeems = isWhatItSeems(
            content = content,
            contentType = contentType
        )
        return when (isWhatItSeems) {
            true ->  parsedContentType
            else -> throw IllegalArgumentException("Mismatch mellom content og contentType")
        }
    }

    fun isSupportedContentType(
        contentType: String
    ): Boolean {
        val parsedContentType = ContentType.parseOrNull(contentType) ?: return false

        return when(supportedContentTypes.contains(parsedContentType)){
            true -> true
            false -> {
                logger.error("Ikke støttet contentType: {}. Støttet contentType: {}", parsedContentType, supportedContentTypes)
                false
            }
        }
    }


    fun isWhatItSeems(
        content: ByteArray,
        contentType: String
    ): Boolean {
        val parsedContentType = ContentType.parseOrNull(contentType) ?: return false

        val detected = tika.detectOrNull(content) ?: return false
        val parsed = ContentType.parseOrNull(detected) ?: return false

        if (PLAIN_TEXT == parsed && JSON == parsedContentType) {
            return try {
                objectMapper.readTree(content)
                true
            } catch (cause: Throwable) {
                logger.warn("text/plain dokument inneholder ikke JSON")
                false
            }
        }

        return parsedContentType.toString().equals(tika.detectOrNull(content), ignoreCase = true)
    }
}

private fun Tika.detectOrNull(content: ByteArray): String? {
    return try {
        detect(content)
    } catch (cause: Throwable) {
        logger.warn("Kunne ikke detektere filfytpe for dokument", cause)
        null
    }
}

private fun ContentType.Companion.parseOrNull(contentType: String): ContentType? {
    return try {
        parse(contentType)
    } catch (cause: Throwable) {
        logger.warn("Ugyldig content type $contentType")
        null
    }
}
