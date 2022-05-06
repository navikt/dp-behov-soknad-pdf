package no.nav.dagpenger.soknad.pdf
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import java.io.ByteArrayOutputStream

internal class PdfBuilder {

    internal fun lagPdf(): ByteArray = lagPdf("/søknad.html".fileAsString())

    internal fun lagPdf(html: String): ByteArray {
        return ByteArrayOutputStream().use {
            PdfRendererBuilder()/*.apply {
                for (font in fonts) {
                    useFont({ ByteArrayInputStream(font.bytes) }, font.family, font.weight, font.style, font.subset)
                }
            }*/
                .usePdfAConformance(PdfRendererBuilder.PdfAConformance.PDFA_2_U)
                .usePdfUaAccessbility(true)
                .withHtmlContent(html, null)
                .toStream(it)
                .run()
            it.toByteArray()
        }
    }
}
