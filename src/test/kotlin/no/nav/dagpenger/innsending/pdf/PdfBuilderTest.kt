package no.nav.dagpenger.innsending.pdf

import no.nav.dagpenger.innsending.html.HtmlBuilder
import no.nav.dagpenger.innsending.html.TestModellHtml
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider
import org.verapdf.pdfa.Foundries
import org.verapdf.pdfa.flavours.PDFAFlavour
import org.verapdf.pdfa.results.TestAssertion
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.test.assertTrue

internal class PdfBuilderTest {
    @Test
    fun `Kan lage PDF fra HTML`() {
        assertDoesNotThrow {
            PdfBuilder.lagPdf(HtmlBuilder.lagBruttoHtml(TestModellHtml.innsending)).also {
                File("build/tmp/test/søknad.pdf").writeBytes(it)
            }
        }
    }

    @Test
    fun `Møter PdfA og UA standardene`() {
        VeraGreenfieldFoundryProvider.initialise()
        Foundries.defaultInstance().use { foundry ->
            val pdf = ByteArrayInputStream(PdfBuilder.lagPdf(HtmlBuilder.lagBruttoHtml(TestModellHtml.innsending)))
            val validator = foundry.createValidator(PDFAFlavour.PDFA_2_U, true)
            foundry.createParser(pdf, PDFAFlavour.PDFA_2_U).also { parser ->
                val result =
                    validator
                        .validate(parser)
                        .testAssertions
                        .filter {
                            it.status == TestAssertion.Status.FAILED
                        }.distinctBy { it.ruleId }
                assertTrue(
                    result.isEmpty(),
                    "PDF-A verifisering feiler på :\n ${result.map { it.ruleId }}," +
                        " se https://docs.verapdf.org/validation/pdfa-parts-2-and-3/",
                )
            }
        }
    }
}
