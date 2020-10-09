package no.nav.helse.dokument.crypto

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import no.nav.helse.dokument.eier.Eier
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.IllegalStateException

private val logger: Logger = LoggerFactory.getLogger("nav.Cryptography")

class Cryptography(
    private val encryptionPassphrase: Pair<Int, String>,
    private val decryptionPassphrases: Map<Int, String>
) {

    init {
        logger.info("Genererer ID'er med Nøkkel ID = ${encryptionPassphrase.first}")
        logger.info("Decrypterer med ${decryptionPassphrases.size} mulige Nøkkel ID'er:")
        decryptionPassphrases.forEach{ logger.info("${it.key}")}
    }

    fun encrypt(id: String,
                plainText : String,
                eier: Eier
    ) : String {
        logger.trace("Krypterer ID $id")
        val keyId = extractKeyId(id)
        logger.trace("Krypterer med Nøkkel ID $keyId")

        return Crypto(
            passphrase = getPasshrase(keyId),
            iv = eier.id
        ).encrypt(plainText)
    }

    fun decrypt(id: String,
                encrypted: String,
                eier: Eier
    ) : String {
        logger.trace("Decrypterer ID $id")
        val keyId = extractKeyId(id)
        logger.trace("Dekrypterer med Nøkkel ID $keyId")

        return Crypto(
            passphrase = getPasshrase(keyId),
            iv = eier.id
        ).decrypt(encrypted)
    }

    fun id(id: String = UUID.randomUUID().toString()) : String {
        val jwt = JWT.create()
            .withKeyId(encryptionPassphrase.first.toString())
            .withJWTId(id)
            .sign(Algorithm.none())
            .removeSuffix(".")
        logger.trace("Genrerert ID er $jwt")
        if (logger.isTraceEnabled) {
            val decoded = decodeId(jwt)
            val headers = String(Base64.getDecoder().decode(decoded.header))
            val payload = String(Base64.getDecoder().decode(decoded.payload))
            logger.trace("Headers=$headers")
            logger.trace("Payload=$payload")
        }
        return jwt
    }

    private fun decodeId(id: String) = JWT.decode(if(id.endsWith(".")) id else "$id.")

    private fun extractKeyId(id: String) = decodeId(id).keyId.toInt()

    private fun getPasshrase(keyId: Int) : String {
        if (!decryptionPassphrases.containsKey(keyId)) {
            throw IllegalStateException("Har inget passord tilgjengelig for Nøkkel ID $keyId. Får ikke gjort encrypt/decrypt.")
        }
        return decryptionPassphrases[keyId]!!
    }
}