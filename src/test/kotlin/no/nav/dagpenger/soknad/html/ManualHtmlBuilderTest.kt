package no.nav.dagpenger.soknad.html

import no.nav.dagpenger.soknad.html.TestHtml.testHtml
import no.nav.dagpenger.soknad.pdf.PdfBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.io.File

class ManualHtmlBuilderTest {

    @Test
    fun manuellTest() {
        assertDoesNotThrow {
            testHtml.also { generertHtml ->
                File("build/tmp/test/søknad.html").writeText(generertHtml)
            /*    PdfBuilder().lagPdf(generertHtml).also { generertPdf ->
                    File("build/tmp/test/søknad.pdf").writeBytes(generertPdf)
                }*/
            }
        }
    }
}
