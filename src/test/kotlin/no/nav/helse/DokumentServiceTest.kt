package no.nav.helse

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.helse.dokument.*
import no.nav.helse.dokument.crypto.Cryptography
import no.nav.helse.dokument.eier.Eier
import no.nav.helse.dokument.storage.Storage
import no.nav.helse.dokument.storage.StorageKey
import no.nav.helse.dokument.storage.StorageValue
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val logger: Logger = LoggerFactory.getLogger("nav.DokumentServiceTest")

class DokumentServiceTest {

    @Test
    fun `Rullering av passord for kryptering fungerer slik at dokumenter kryptert foer endringen fortsatt kan dekrypteres og hentes ut`() {
        // Setup
        val storage = InMemoryStorage()
        val virusScannerMock = mockk<VirusScanner>()
        every { runBlocking { virusScannerMock.scan(any()) }}.answers {  }


        val eier1 = Eier("12345")
        val eier2 = Eier("678910")
        val eier3 = Eier("11121314")

        val dokument1 = Dokument(
            title = "Tittel1",
            content = byteArrayOf(1,2,3,4),
            contentType = "application/pdf"
        )
        val dokument2 = Dokument(
            title = "Tittel2",
            content = byteArrayOf(5,6,7,8),
            contentType = "image/png"
        )
        val dokument3 = Dokument(
            title = "Tittel3",
            content = byteArrayOf(9,10,11,12),
            contentType = "image/jpeg"
        )


        val passord1 = Pair(1, "passord")
        val passord2 = Pair(2, "passord2")
        val passord3 = Pair(3, "passord3")


        val dokumentService1 = DokumentService(
            storage = storage,
            cryptography = Cryptography(
                encryptionPassphrase = passord1,
                decryptionPassphrases = mapOf(passord1)
            ),
            virusScanner = virusScannerMock
        )

        val dokumentService2 = DokumentService(
            storage = storage,
            cryptography = Cryptography(
                encryptionPassphrase = passord2,
                decryptionPassphrases = mapOf(passord1, passord2)
            ),
            virusScanner = virusScannerMock
        )

        val dokumentService3 = DokumentService(
            storage = storage,
            cryptography = Cryptography(
                encryptionPassphrase = passord3,
                decryptionPassphrases = mapOf(passord1, passord2, passord3)
            ),
            virusScanner = virusScannerMock
        )

        // Test
        // Lagrer de tre dokumentene so bruker hver sitt passord
        runBlocking {
            val dokumentId1 = dokumentService1.lagreDokument(
                dokument = dokument1,
                eier = eier1
            )
            val dokumentId2 = dokumentService2.lagreDokument(
                dokument = dokument2,
                eier = eier2
            )
            val dokumentId3 = dokumentService3.lagreDokument(
                dokument = dokument3,
                eier = eier3
            )

            // dokumentId3 bør kun kunne bli hentet av dokumentService3
            hentOgAssertDokument(dokumentService = dokumentService1, dokumentId = dokumentId3, eier = eier3, expectedDokument = dokument3, expectOk = false)
            hentOgAssertDokument(dokumentService = dokumentService2, dokumentId = dokumentId3, eier = eier3, expectedDokument = dokument3, expectOk = false)
            hentOgAssertDokument(dokumentService = dokumentService3, dokumentId = dokumentId3, eier = eier3, expectedDokument = dokument3, expectOk = true)


            // dokumentId2 bør kunne hentes både med dokumentService2 og dokumentService3
            hentOgAssertDokument(dokumentService = dokumentService1, dokumentId = dokumentId2, eier = eier2, expectedDokument = dokument2, expectOk = false)
            hentOgAssertDokument(dokumentService = dokumentService2, dokumentId = dokumentId2, eier = eier2, expectedDokument = dokument2, expectOk = true)
            hentOgAssertDokument(dokumentService = dokumentService3, dokumentId = dokumentId2, eier = eier2, expectedDokument = dokument2, expectOk = true)

            // dokumentId1 bør kunne hentes med alle servicene
            hentOgAssertDokument(dokumentService = dokumentService1, dokumentId = dokumentId1, eier = eier1, expectedDokument = dokument1, expectOk = true)
            hentOgAssertDokument(dokumentService = dokumentService2, dokumentId = dokumentId1, eier = eier1, expectedDokument = dokument1, expectOk = true)
            hentOgAssertDokument(dokumentService = dokumentService3, dokumentId = dokumentId1, eier = eier1, expectedDokument = dokument1, expectOk = true)
        }
    }


    private fun hentOgAssertDokument(
        dokumentService: DokumentService,
        dokumentId: DokumentId,
        eier: Eier,
        expectedDokument: Dokument,
        expectOk: Boolean
    ) {
        try {
            val dokument = dokumentService.hentDokument(
                dokumentId = dokumentId,
                eier = eier
            )
            assertEquals(expectedDokument, dokument)
            assertTrue(expectOk)
        } catch (cause: Throwable) {
            if (expectOk) {
                logger.error("Feil ved henting", cause)
            }
            assertFalse(expectOk) // Om det oppstår en exception må expectOk == false
        }
    }

    private class InMemoryStorage : Storage {
        override fun ready() {}

        private val storage = mutableMapOf<StorageKey, StorageValue>()

        override fun slett(storageKey: StorageKey) : Boolean {
            val value = storage.remove(storageKey)
            return value != null
        }

        override fun lagre(key: StorageKey, value: StorageValue) {
            storage[key] = value
        }

        override fun lagre(key: StorageKey, value: StorageValue, expires: ZonedDateTime) {
            lagre(key, value)
        }

        override fun hent(key: StorageKey): StorageValue? {
            return storage[key]
        }
    }
}