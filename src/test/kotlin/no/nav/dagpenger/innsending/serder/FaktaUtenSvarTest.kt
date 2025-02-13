package no.nav.dagpenger.innsending.serder

import io.kotest.assertions.throwables.shouldNotThrowAny
import no.nav.dagpenger.innsending.html.HtmlBuilder
import no.nav.dagpenger.innsending.html.Innsending
import no.nav.dagpenger.innsending.pdf.PdfBuilder
import org.junit.jupiter.api.Test
import java.io.File
import java.time.ZonedDateTime

private val resourceRetriever = object {}.javaClass

class FaktaUtenSvarTest {
    private val faktaJson = resourceRetriever.getResource("/fakta_uten_svar.json")?.readText()!!
    private val dokumentasjonKravJson = resourceRetriever.getResource("/dokumentasjonkrav_uten_svar.json")?.readText()!!
    private val tekstJson = resourceRetriever.getResource("/tekst.json")?.readText()!!

    @Test
    fun `Debug test`() {
        shouldNotThrowAny {
            JsonHtmlMapper(
                innsendingsData = faktaJson,
                dokumentasjonKrav = dokumentasjonKravJson,
                tekst = tekstJson,
                språk = Innsending.InnsendingsSpråk.BOKMÅL,
            ).parse()
                .apply {
                    infoBlokk =
                        Innsending.InfoBlokk(
                            fødselsnummer = "ident",
                            innsendtTidspunkt = ZonedDateTime.now(),
                            navn = "Ola Nordmann",
                            adresse = "Kardemomme By, 4609 Kristiansand, Norge",
                        )
                }.let {
                    PdfBuilder
                        .lagPdf(HtmlBuilder.lagBruttoHtml(it))
                        .let { pdf -> File("build/tmp/test/bug_tom_svar.pdf").writeBytes(pdf) }
                }
        }
    }
}
