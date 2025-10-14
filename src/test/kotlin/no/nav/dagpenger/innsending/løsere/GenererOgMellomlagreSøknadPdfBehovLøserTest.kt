@file:Suppress("ktlint:standard:property-naming")

package no.nav.dagpenger.innsending.løsere

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.dagpenger.innsending.ArkiverbartDokument.DokumentVariant.BRUTTO
import no.nav.dagpenger.innsending.ArkiverbartDokument.DokumentVariant.NETTO
import no.nav.dagpenger.innsending.LagretDokument
import no.nav.dagpenger.innsending.løsere.GenererOgMellomlagreSøknadPdfBehovLøser.Companion.BEHOV
import no.nav.dagpenger.innsending.pdf.PdfLagring
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import java.util.UUID

internal class GenererOgMellomlagreSøknadPdfBehovLøserTest {
    private val pdfLagring = mockk<PdfLagring>()
    private val søknadId: UUID = UUID.randomUUID()
    private val ident = "12345678910"

    val testRapid =
        TestRapid().also {
            GenererOgMellomlagreSøknadPdfBehovLøser(
                rapidsConnection = it,
                pdfLagring =
                    pdfLagring.also { pdfLagring ->
                        coEvery {
                            pdfLagring.lagrePdf(
                                søknadUUid = søknadId.toString(),
                                arkiverbartDokument = any(),
                                fnr = ident,
                            )
                        } returns
                            listOf(
                                LagretDokument("urn:vedlegg:soknadId/netto.pdf", NETTO, "netto.pdf"),
                                LagretDokument("urn:vedlegg:soknadId/brutto.pdf", BRUTTO, "brutto.pdf"),
                            )
                    },
            )
        }

    @Test
    fun `GenererOgMellomlagreSøknadPdfBehovLøser besvarer behov som forventet`() {
        testRapid.sendTestMessage(testmeldingUtenLøsning)

        testRapid.inspektør.size shouldBe 1
        testRapid.inspektør.message(0)["@løsning"][BEHOV] shouldBe jacksonObjectMapper().readTree(forventetLøsning)
    }

    @Test
    fun `GenererOgMellomlagreSøknadPdfBehovLøser besvarer ikke behov hvis melding inneholder @løsning`() {
        testRapid.sendTestMessage(testmeldingMedLøsning)

        testRapid.inspektør.size shouldBe 0
    }

    @Test
    fun `GenererOgMellomlagreSøknadPdfBehovLøser kaster exception hvis generering av PDF feiler`() {
        coEvery {
            pdfLagring.lagrePdf(
                søknadUUid = søknadId.toString(),
                arkiverbartDokument = any(),
                fnr = ident,
            )
        } throws Exception("kaboom")

        val exception = shouldThrow<Exception> { testRapid.sendTestMessage(testmeldingUtenLøsning) }

        exception.message shouldBe "kaboom"
        testRapid.inspektør.size shouldBe 0
    }

    @Language("JSON")
    val forventetLøsning =
        """
        [
               {
                 "metainfo": {
                   "innhold": "netto.pdf",
                   "filtype": "PDF", 
                   "variant": "NETTO"
                 },
                 "urn": "urn:vedlegg:soknadId/netto.pdf"
               },
               {
                 "metainfo": {
                   "innhold": "brutto.pdf",
                   "filtype": "PDF",
                   "variant": "BRUTTO"
                 },
                 "urn": "urn:vedlegg:soknadId/brutto.pdf"
               }
             ]
        """.trimIndent()

    @Language("JSON")
    val testmeldingUtenLøsning =
        """
         {
            "@event_name": "behov",
            "@behov": ["$BEHOV"],
            "søknadId": "$søknadId",
            "nettoPayload": "<html><head><title>NETTO</title></head><body><p>NETTO</p></body></html>",
            "bruttoPayload": "<html><head><title>BRUTTO</title></head><body><p>BRUTTO</p></body></html>",
            "ident": "$ident"
        }
        """.trimIndent()

    @Language("JSON")
    val testmeldingMedLøsning =
        """
         {
            "@event_name": "behov",
            "@behov": ["$BEHOV"],
            "søknadId": "$søknadId",
            "nettoPayload": "<html><head><title>NETTO</title></head><body><p>NETTO</p></body></html>",
            "bruttoPayload": "<html><head><title>BRUTTO</title></head><body><p>BRUTTO</p></body></html>",
            "ident": "$ident",
            "@løsning": "something-something"
        }
        """.trimIndent()
}
