package no.nav.dagpenger.innsending.serder

import no.nav.dagpenger.innsending.html.HtmlBuilder
import no.nav.dagpenger.innsending.html.Innsending
import no.nav.dagpenger.innsending.html.Innsending.Hjelpetekst
import no.nav.dagpenger.innsending.html.InnsendingSupplier
import no.nav.dagpenger.innsending.pdf.PdfBuilder
import no.nav.dagpenger.innsending.serder.Oppslag.TekstObjekt.FaktaTekstObjekt
import no.nav.dagpenger.innsending.serder.Oppslag.TekstObjekt.SeksjonTekstObjekt
import no.nav.dagpenger.innsending.serder.Oppslag.TekstObjekt.SvaralternativTekstObjekt
import org.intellij.lang.annotations.Language
import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.io.File
import java.time.ZonedDateTime
import kotlin.test.assertNotNull

private val resourceRetriever = object {}.javaClass

internal class SerderTest {
    private val faktaJson = resourceRetriever.getResource("/fakta.json")?.readText()!!
    private val debugfaktaJson = resourceRetriever.getResource("/debugfakta.json")?.readText()!!
    private val tekstJson = resourceRetriever.getResource("/tekst.json")?.readText()!!
    private val debugtekstJson = resourceRetriever.getResource("/debugtekst.json")?.readText()!!
    private val dokumentasjonKravJson = resourceRetriever.getResource("/dokumentasjonkrav.json")?.readText()!!
    private val oppslag = Oppslag(tekstJson)

    @Test
    fun `Debug test`() {
        val mappetInnsending =
            JsonHtmlMapper(
                innsendingsData = debugfaktaJson,
                dokumentasjonKrav = dokumentasjonKravJson,
                tekst = debugtekstJson,
                språk = Innsending.InnsendingsSpråk.BOKMÅL,
            ).parse().apply {
                infoBlokk =
                    Innsending.InfoBlokk(
                        fødselsnummer = "ident",
                        innsendtTidspunkt = ZonedDateTime.now(),
                        navn = "Ola Nordmann",
                        adresse = "Kardemomme By, 4609 Kristiansand, Norge",
                    )
            }

        assertIngenTommehjelpetekster(mappetInnsending)
        HtmlBuilder.lagNettoHtml(mappetInnsending).also { PdfBuilder.lagPdf(it) }
        HtmlBuilder.lagBruttoHtml(mappetInnsending).also {
            PdfBuilder.lagPdf(it)
            assertEquals(
                0,
                Jsoup
                    .parse(it)
                    .getElementsByClass("hjelpetekst")
                    .filter { t -> t.childrenSize() < 1 }
                    .size,
                "fant tomme hjelpetekster",
            )
        }
    }

    @Test
    fun `parser søknadstekst riktig`() {
        oppslag.lookup<SeksjonTekstObjekt>("seksjon1").also {
            assertEquals("seksjon1", it.textId)
            assertEquals("Tittel for seksjon 1", it.title)
            assertEquals("Hjelpetekst med overskrift til seksjon", it.helpText?.title)
            @Suppress("ktlint:standard:max-line-length")
            val expcextedBody =
                """<p>Her er en hjelpetekst tekst som hjelper veldig mye når en trenger hjelp. Med superhjelpsom <a href="https://nav.no/superhjelpen">lenke</a></p>"""
            assertEquals(expcextedBody, it.helpText?.body?.html)
            @Language("HTML")
            val expectedDescription =
                """<p>description for seksjon</p><p>tadda, det her går jo bra!</p>"""
            assertEquals(expectedDescription, it.description?.html?.replace("\n", ""))
        }

        oppslag.lookup<FaktaTekstObjekt>("f3").also {
            assertEquals("f3", it.textId)
            assertEquals(
                "Her blir det spurt om noe som du kan svare ja eller nei på. Svarer du ja eller nei?",
                it.text,
            )
            assertEquals(
                "<p>Hjelpetekst</p>",
                it.helpText
                    ?.body
                    ?.html
                    ?.replace(" ", ""),
            )
            assertNull(it.description)
        }
    }

    @Test
    fun `parser svaralternativ riktig`() {
        oppslag.lookup<SvaralternativTekstObjekt>("svaralternativ1").also { oppslag ->
            assertEquals("svaralternativ1", oppslag.textId)
            assertEquals(
                "Vet ikke helt hva dte her skal brukes til enda, men gjetter på at vi finner det ut",
                oppslag.text,
            )
            require(oppslag.alertText != null)
            oppslag.alertText.also { alerttext ->
                assertEquals(
                    "<p>Her er ett og annet som er greit å vite hvios du har valgt svaralternativ1</p>",
                    alerttext.body!!.html,
                )
                assertEquals("Her er noe info", alerttext.title)
                assertEquals("info", alerttext.type)
            }
        }
        assertDoesNotThrow {
            oppslag.lookup<SvaralternativTekstObjekt>("flervalg1").also {
                assertNotNull(it.alertText)
                assertNull(it.alertText.title)
            }
            oppslag.lookup<SvaralternativTekstObjekt>("flervalg2").also {
                assertNull(it.alertText)
            }
        }
    }

    @Test
    fun `lager html og pfd fra json`() {
        assertDoesNotThrow {
            val innsending =
                JsonHtmlMapper(
                    innsendingsData = debugfaktaJson,
                    dokumentasjonKrav = dokumentasjonKravJson,
                    tekst = debugtekstJson,
                    språk = Innsending.InnsendingsSpråk.BOKMÅL,
                ).parse(InnsendingSupplier.InnsendingType.DAGPENGER).apply {
                    infoBlokk =
                        Innsending.InfoBlokk(
                            fødselsnummer = "ident",
                            innsendtTidspunkt = ZonedDateTime.now(),
                            navn = "Ola Nordmann",
                            adresse = "Kardemomme By, 4609 Kristiansand, Norge",
                        )
                }

            HtmlBuilder.lagBruttoHtml(innsending).also {
                File("build/tmp/test/søknad_brutto.html").writeText(it)
                PdfBuilder.lagPdf(it).also { generertPdf ->
                    File("build/tmp/test/søknad_brutto.pdf").writeBytes(generertPdf)
                }
            }

            HtmlBuilder.lagNettoHtml(innsending).also {
                File("build/tmp/test/søknad_netto.html").writeText(it)
                PdfBuilder.lagPdf(it).also { generertPdf ->
                    File("build/tmp/test/søknad_netto.pdf").writeBytes(generertPdf)
                }
            }
        }
    }

    @Test
    fun `lager html og pfd for ettersending`() {
        assertDoesNotThrow {
            val innsending =
                JsonHtmlMapper(
                    innsendingsData = null,
                    dokumentasjonKrav = dokumentasjonKravJson,
                    tekst = debugtekstJson,
                    språk = Innsending.InnsendingsSpråk.BOKMÅL,
                ).parseEttersending().apply {
                    infoBlokk =
                        Innsending.InfoBlokk(
                            fødselsnummer = "ident",
                            innsendtTidspunkt = ZonedDateTime.now(),
                            navn = "Ola Nordmann",
                            adresse = "Kardemomme By, 4609 Kristiansand, Norge",
                        )
                }

            HtmlBuilder.lagEttersendingHtml(innsending).also {
                File("build/tmp/test/ettersending.html").writeText(it)
                PdfBuilder.lagPdf(it).also { generertPdf ->
                    File("build/tmp/test/ettersending.pdf").writeBytes(generertPdf)
                }
            }
        }
    }

    @Test
    fun `fjerner tags som ikke er tillatt`() {
        oppslag.`portable objekter i testfil`().forEach { tekstobjekt ->
            tekstobjekt.description?.let { assertIngenUlovligeTagger(it) }
            tekstobjekt.helpText?.body?.let { assertIngenUlovligeTagger(it) }
        }
    }
}

private fun assertIngenUlovligeTagger(htmlString: RawHtmlString) {
    listOf("<script>", "<img>", "<iframe>").forEach {
        assertFalse(
            htmlString.html.contains(it.toRegex()),
            "string ${htmlString.html} inneholder ulovlig tag $it",
        )
    }
}

private fun Oppslag.`portable objekter i testfil`() =
    listOf(
        this.lookup<FaktaTekstObjekt>("f15"),
        this.lookup<FaktaTekstObjekt>("flervalg7"),
        this.lookup<SvaralternativTekstObjekt>("flervalg1"),
        this.lookup<FaktaTekstObjekt>("periode10"),
        this.lookup<FaktaTekstObjekt>("desimaltall3"),
        this.lookup<FaktaTekstObjekt>("f1"),
        this.lookup<FaktaTekstObjekt>("f3"),
        this.lookup<FaktaTekstObjekt>("f67"),
        this.lookup<SeksjonTekstObjekt>("seksjon1"),
        this.lookup<SvaralternativTekstObjekt>("svaralternativ1"),
    )

private fun assertIngenTommehjelpetekster(innsending: Innsending) {
    innsending.seksjoner.forEach { seksjon ->
        seksjon.hjelpetekst?.let { assertFalse(it.isEmpty(), "Fant tom hjelpetekst i seksjon ${seksjon.overskrift}") }
        seksjon.spmSvar.forEach { spmSvar ->
            spmSvar.hjelpetekst?.let {
                assertFalse(
                    it.isEmpty(),
                    "Fant tom hjelpetekst på spørsmål ${spmSvar.sporsmal}",
                )
            }
        }
    }
}

private fun Hjelpetekst.isEmpty(): Boolean = this.tittel.isNullOrEmpty() && this.unsafeHtmlBody.isNullOrEmpty()

private fun Innsending.UnsafeHtml?.isNullOrEmpty(): Boolean = this?.kode.isNullOrEmpty()
