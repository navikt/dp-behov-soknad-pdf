package no.nav.dagpenger.innsending.pdf

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import mu.KotlinLogging
import java.io.ByteArrayOutputStream
import java.io.InputStream

internal object PdfBuilder {
    private val sikkerlogg = KotlinLogging.logger("tjenestekall.PdfBuilder")

    private data class Font(
        val family: String,
        val path: String,
        val weight: Int,
        val style: BaseRendererBuilder.FontStyle,
        val subset: Boolean,
    ) {
        fun inputStreamSupplier(): () -> InputStream = { path.fileAsInputStream() }
    }

    private val fonts: List<Font> =
        listOf(
            Font(
                family = "Source Sans Pro",
                path = "/fonts/SourceSansPro-Normal.ttf",
                weight = 400,
                style = BaseRendererBuilder.FontStyle.NORMAL,
                subset = false,
            ),
            Font(
                family = "Source Sans Pro",
                path = "/fonts/SourceSansPro-Italic.ttf",
                weight = 300,
                style = BaseRendererBuilder.FontStyle.ITALIC,
                subset = false,
            ),
            Font(
                family = "Source Sans Pro",
                path = "/fonts/SourceSansPro-Semibold.ttf",
                weight = 600,
                style = BaseRendererBuilder.FontStyle.NORMAL,
                subset = false,
            ),
        )

    internal fun lagPdf(html: String): ByteArray =
        try {
            ByteArrayOutputStream().use {
                PdfRendererBuilder()
                    .apply {
                        fonts.forEach { font ->
                            useFont(
                                // supplier =
                                font.inputStreamSupplier(),
                                // fontFamily =
                                font.family,
                                // fontWeight =
                                font.weight,
                                // fontStyle =
                                font.style,
                                // subset =
                                font.subset,
                            )
                        }
                    }.usePdfAConformance(PdfRendererBuilder.PdfAConformance.PDFA_2_U)
                    .usePdfUaAccessbility(true)
                    .useColorProfile("/sRGB2014.icc".fileAsByteArray())
                    .defaultTextDirection(BaseRendererBuilder.TextDirection.LTR)
                    .withHtmlContent(html, null)
                    .toStream(it)
                    .run()
                it.toByteArray()
            }
        } catch (e: Exception) {
            sikkerlogg.error(e) { "Kunne ikke lage PDF av søknaden. HTML=$html" }
            throw e
        }
}
