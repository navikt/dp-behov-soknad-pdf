package no.nav.dagpenger.soknad.serder

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.dagpenger.soknad.html.HtmlModell
import java.time.LocalDateTime

internal class JsonHtmlMapper(
    private val ident: String,
    private val søknadsData: String,
    tekst: String,
    private val språk: HtmlModell.SøknadSpråk = HtmlModell.SøknadSpråk.BOKMÅL
) {
    private val oppslag = Oppslag(tekst)
    private val objectMapper = jacksonObjectMapper()

    private fun parse(søknadsData: String): List<HtmlModell.Seksjon> {
        return objectMapper.readTree(søknadsData)["seksjoner"].map {
            val tekstObjekt = oppslag.lookup(it["beskrivendeId"].asText()) as Oppslag.TekstObjekt.SeksjonTekstObjekt
            HtmlModell.Seksjon(
                overskrift = tekstObjekt.title,
                beskrivelse = tekstObjekt.description,
                hjelpetekst = tekstObjekt.helpText,
                spmSvar = it.fakta()
            )
        }
    }

    private fun JsonNode.svar(): String {
        return when (this["type"].asText()) {
            "string" -> this["svar"].asText()
            "boolean" -> språk.boolean(this["svar"].asBoolean())
            "generator" -> "generator"
            else -> throw IllegalArgumentException("hubba")
        }
    }

    private fun JsonNode.fakta(): List<HtmlModell.SporsmalSvar> {
        return this["fakta"].map { node ->
            val tekstObjekt = oppslag.lookup(node["beskrivendeId"].asText()) as Oppslag.TekstObjekt.FaktaTekstObjekt
            HtmlModell.SporsmalSvar(
                sporsmal = tekstObjekt.text,
                svar = node.svar(),
                beskrivelse = tekstObjekt.description,
                hjelpeTekst = tekstObjekt.helpText,
                oppfølgingspørmål = listOf()
            )
        }
    }

    fun parse(): HtmlModell {
        return HtmlModell(
            seksjoner = parse(søknadsData),
            metaInfo = HtmlModell.MetaInfo(språk = HtmlModell.SøknadSpråk.BOKMÅL),
            pdfAKrav = HtmlModell.PdfAKrav(description = "description", subject = "subject", author = "author"),
            infoBlokk = HtmlModell.InfoBlokk(
                fødselsnummer = this.ident,
                datoFerdigstilt = LocalDateTime.now()
            ) // todo finne ut hvordan vi får tak i innsendt dato, kan ikke dette bare legges på behovet?
        )
    }
}
