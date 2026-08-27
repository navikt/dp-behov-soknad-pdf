package no.nav.dagpenger.innsending.løsere

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import de.redsix.pdfcompare.CompareResultImpl
import de.redsix.pdfcompare.PdfComparator
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import no.nav.dagpenger.innsending.ArkiverbartDokument
import no.nav.dagpenger.innsending.ArkiverbartDokument.DokumentVariant.NETTO
import no.nav.dagpenger.innsending.LagretDokument
import no.nav.dagpenger.innsending.pdf.PdfLagring
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import kotlin.test.assertEquals

internal class RapporteringPdfBehovLøserTest {
    val periodeId = "6c43443b-5048-450c-964b-0235f89449fa"
    val testFnr = "12345678910"

    val slot = slot<List<ArkiverbartDokument>>()
    val testRapid =
        TestRapid().also {
            RapporteringPdfBehovLøser(
                rapidsConnection = it,
                pdfLagring =
                    mockk<PdfLagring>().also {
                        coEvery {
                            it.lagrePdf(
                                periodeId,
                                capture(slot),
                                testFnr,
                            )
                        } returns
                            listOf(
                                LagretDokument("urn:vedlegg:journalpostId/netto.pdf", NETTO, "netto.pdf"),
                            )
                    },
            )
        }

    @Test
    fun `besvarer pdf behov for rapportering`() {
        testRapid.sendTestMessage(testMessage)
        assertEquals(1, testRapid.inspektør.size)
        assertJsonEquals(
            expectedLøsning,
            testRapid.inspektør.message(0)["@løsning"],
        )

        val referenceFile = File("src/test/resources/rapportering.pdf")
        val createdFile = File("build/tmp/test/rapportering.pdf")
        val diffFile = File("build/tmp/test/rapportering_diff")
        slot.captured.let { dokumenter ->
            createdFile.writeBytes(dokumenter[0].pdf)
        }

        val equal = PdfComparator<CompareResultImpl>(referenceFile.path, createdFile.path).compare().writeTo(diffFile.path)

        if (!equal) {
            throw Exception("PDFene er ikke like")
        }
    }

    @Test
    fun `besvarer ikke behov hvis løsning er besvart`() {
        testRapid.sendTestMessage(testMessageMedLøsning)
        assertEquals(0, testRapid.inspektør.size)
    }

    private fun assertJsonEquals(
        expected: String,
        actual: JsonNode,
    ) {
        val objectMapper = jacksonObjectMapper()
        assertEquals(objectMapper.readTree(expected), actual)
    }

    private val json =
        """
        {
          "timestamp":"2023-10-23T18:53:07.614763446",
          "claims":{
            "sub":{"missing":false,"null":false},"iss":{"missing":false,"null":false}
          },
          "image":"ghcr.io/navikt/dp-rapportering-frontend:294a16920167022439d00c646b2c03e5742a1470",
          "kildekode":"294a16920167022439d00c646b2c03e5742a1470",
          "klient":"node",
          "språk":"no-NB",
          "rapportering":{
            "2023-07-31":{"Arbeid":54000000000000},
            "2023-08-01":{"Arbeid":54000000000000},
            "2023-08-02":{"Arbeid":28800000000000},
            "2023-08-03":{},
            "2023-08-04":{},
            "2023-08-05":{},
            "2023-08-06":{},
            "2023-08-07":{},
            "2023-08-08":{},
            "2023-08-09":{"Syk":172800000000000},
            "2023-08-10":{},
            "2023-08-11":{"Ferie":172800000000000},
            "2023-08-12":{},
            "2023-08-13":{}
          },
          "@id":"99c0df05-6ce7-4bf4-b46a-80c4bc3b1041",
          "@opprettet":"2023-10-23T18:53:07.730688948",
          "system_read_count":0,
          "system_participating_services":[{"id": "99c0df05-6ce7-4bf4-b46a-80c4bc3b1041", "service": "dp-rapportering"}]
        }
        """.trimIndent().replace("\"", "\\\"").replace("\n", "")

    private val expectedLøsning =
        """
        {
           "${RapporteringPdfBehovLøser.BEHOV}": [
              {
                "metainfo": {
                  "innhold": "netto.pdf",
                  "filtype": "PDF", 
                  "variant": "NETTO"
                },
                "urn": "urn:vedlegg:journalpostId/netto.pdf"
              }
           ]
        }
        """.trimIndent()

    private val testMessage =
        """
        {
          "@event_name":"behov",
          "@behovId":"eb1ae7a9-d314-4f4a-a5e0-360b537ca11f",
          "@behov":["MellomlagreRapportering"],
          "meldingsreferanseId":"d0ce2eef-ab53-4b06-acf3-4c85386dc561",
          "ident":"$testFnr",
          "MellomlagreRapportering":{
              "periodeId":"$periodeId",
              "json":"$json"
          },
          "@id":"30ef9625-196a-445b-9b4e-67e0e6a5118d",
          "@opprettet":"2023-10-23T18:53:08.056035121",
          "system_read_count":1,
          "system_participating_services":[{"id": "30ef9625-196a-445b-9b4e-67e0e6a5118d", "service": "dp-rapportering"}]
        }
        """.trimIndent()

    private val testMessageMedLøsning =
        """
        {
          "@event_name":"behov",
          "@behovId":"eb1ae7a9-d314-4f4a-a5e0-360b537ca11f",
          "@behov":["MellomlagreRapportering"],
          "@løsning": "something",
          "meldingsreferanseId":"d0ce2eef-ab53-4b06-acf3-4c85386dc561",
          "ident":"$testFnr",
          "MellomlagreRapportering":{
              "periodeId":"$periodeId",
              "json":"$json"
          },
          "@id":"30ef9625-196a-445b-9b4e-67e0e6a5118d",
          "@opprettet":"2023-10-23T18:53:08.056035121",
          "system_read_count":1,
          "system_participating_services":[{"id": "30ef9625-196a-445b-9b4e-67e0e6a5118d", "service": "dp-rapportering"}]
        }
        """.trimIndent()
}
