package no.nav.dagpenger.innsending.serder

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.innsending.html.Innsending
import no.nav.dagpenger.innsending.html.InnsendingSupplier
import no.nav.dagpenger.innsending.serder.Oppslag.TekstObjekt.DokumentkravTekstObjekt
import no.nav.dagpenger.innsending.serder.Oppslag.TekstObjekt.EnkelText
import no.nav.dagpenger.innsending.serder.Oppslag.TekstObjekt.FaktaTekstObjekt
import no.nav.dagpenger.innsending.serder.Oppslag.TekstObjekt.HelpText
import no.nav.dagpenger.innsending.serder.Oppslag.TekstObjekt.SeksjonTekstObjekt
import no.nav.dagpenger.innsending.serder.Oppslag.TekstObjekt.SvaralternativTekstObjekt
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import java.lang.ClassCastException

internal class Oppslag(
    private val tekstJson: String,
) {
    companion object {
        private val logg = KotlinLogging.logger {}

        inline fun <reified T : TekstObjekt> Map<String, TekstObjekt>.lookup(tekstId: String): T {
            val objekt: TekstObjekt =
                this.getOrElse(tekstId) {
                    logg.error { "Fant ikke tekst for tekstId: $tekstId" }
                    lagDummyTekstObjekt<T>(tekstId)
                }
            return try {
                objekt as T
            } catch (e: ClassCastException) {
                logg.warn { "Feil type for $tekstId: $e" }
                lagDummyTekstObjekt<T>(tekstId) as T
            }
        }

        inline fun <reified T : TekstObjekt> lagDummyTekstObjekt(tekstId: String) =
            when (T::class) {
                EnkelText::class -> EnkelText(textId = tekstId, text = tekstId)
                FaktaTekstObjekt::class -> FaktaTekstObjekt(textId = tekstId, text = tekstId)
                SeksjonTekstObjekt::class -> SeksjonTekstObjekt(textId = tekstId, title = tekstId)
                SvaralternativTekstObjekt::class ->
                    SvaralternativTekstObjekt(
                        textId = tekstId,
                        text = tekstId,
                        alertText = null,
                    )

                DokumentkravTekstObjekt::class -> DokumentkravTekstObjekt(textId = tekstId, title = tekstId)
                else -> throw IllegalArgumentException("Ukjent klasse: ${T::class.java.name}")
            }
    }

    internal inline fun <reified T : TekstObjekt> lookup(id: String): T = tekstMap.lookup(id)

    private val objectMapper = jacksonObjectMapper()
    private val tekstMap = parse(tekstJson)

    private fun parse(tekstJson: String): Map<String, TekstObjekt> =
        try {
            objectMapper.readTree(tekstJson).let {
                it.tilFaktaTekstObjekt() + it.tilSeksjonTekstObjekt() +
                    it.tilSvarAlternativTekstObjekt() + it.tilAppTekstObjekt() +
                    it.tilDokumentkravTekstObjekt()
            }
        } catch (e: NullPointerException) {
            logg.error(e) { "Fikk NullPointerException ved parsing av tekster: $tekstJson" }
            throw e
        }

    private fun JsonNode.tilFaktaTekstObjekt(): Map<String, TekstObjekt> {
        val map = mutableMapOf<String, TekstObjekt>()
        fakta().forEach { tekst ->
            val textId = tekst["textId"].asText()
            withLoggingContext("textId" to textId) {
                map[textId] =
                    FaktaTekstObjekt(
                        textId = textId,
                        text = tekst["text"].asText(),
                        description = tekst.get("description")?.asRawHtmlString(),
                        helpText = tekst.hjelpetekst(),
                        unit = tekst.get("unit")?.asText(),
                    )
            }
        }
        return map
    }

    private fun JsonNode.tilSeksjonTekstObjekt(): Map<String, TekstObjekt> {
        val map = mutableMapOf<String, TekstObjekt>()
        seksjoner().forEach { tekst ->
            val textId = tekst["textId"].asText()
            withLoggingContext("textId" to textId) {
                map[textId] =
                    SeksjonTekstObjekt(
                        textId = textId,
                        title = tekst["title"].asText(),
                        description = tekst.get("description")?.asRawHtmlString(),
                        helpText = tekst.hjelpetekst(),
                    )
            }
        }
        return map
    }

    private fun JsonNode.tilAppTekstObjekt(): Map<String, TekstObjekt> {
        val map = mutableMapOf<String, TekstObjekt>()
        apptekster().forEach { tekst ->
            val textId = tekst["textId"].asText()
            withLoggingContext("textId" to textId) {
                map[textId] =
                    EnkelText(
                        textId = textId,
                        text = tekst["valueText"].asText(),
                    )
            }
        }
        return map
    }

    private fun JsonNode.tilDokumentkravTekstObjekt(): Map<String, TekstObjekt> =
        dokumentkrav().associate { dokumentkrav ->
            val textId = dokumentkrav["textId"].asText()
            val title: String =
                when {
                    dokumentkrav["title"].isNull -> {
                        logg.error { "Fant ikke title for $textId" }
                        textId
                    }

                    else -> {
                        dokumentkrav["title"].asText()
                    }
                }
            withLoggingContext("textId" to textId) {
                textId to
                    DokumentkravTekstObjekt(
                        textId = textId,
                        title = title,
                        description = dokumentkrav.get("description")?.asRawHtmlString(),
                        helpText =
                            dokumentkrav["helpText"]?.takeIf { !it.isNull }?.let { helpText ->
                                HelpText(
                                    helpText["title"]?.asText(),
                                    helpText["body"].asRawHtmlString(),
                                )
                            },
                    )
            }
        }

    private fun JsonNode.tilSvarAlternativTekstObjekt(): Map<String, TekstObjekt> {
        val map = mutableMapOf<String, TekstObjekt>()
        (dokumentkravSvaralternativer() + svaralternativer()).forEach { tekst ->
            val textId = tekst["textId"].asText()
            withLoggingContext("textId" to textId) {
                map[textId] =
                    SvaralternativTekstObjekt(
                        textId = textId,
                        text =
                            when (tekst.hasNonNull("text")) {
                                true -> tekst["text"].asText()
                                false -> hentTekst(textId)
                            },
                        alertText =
                            tekst["alertText"]?.takeIf { !it.isNull }?.let { alerttext ->
                                // todo fixme
                                TekstObjekt.AlertText(
                                    alerttext["title"]?.asText(),
                                    alerttext["type"]?.asText() ?: "error",
                                    alerttext["body"]?.asRawHtmlString(),
                                )
                            },
                    )
            }
        }
        return map
    }

    // todo: Fjerne denne når vi har fått riktig tekst fra sanity
    private fun hentTekst(textId: String): String {
        logg.warn { "Fant ikke tekst for '$textId' i sanity-json" }
        return when (textId) {
            "dokumentkrav.svar.send.naa" -> "Laste opp nå"
            "dokumentkrav.svar.sender.ikke" -> "Jeg sender det ikke"
            "dokumentkrav.svar.sendt.tidligere" -> "Jeg har sendt det i en tidligere søknad om dagpenger"
            "dokumentkrav.svar.send.senere" -> "Jeg sender det senere"
            "dokumentkrav.svar.andre.sender" -> "Noen andre sender det for meg"
            else -> textId
        }
    }

    internal fun generellTekst(innsendingType: InnsendingSupplier.InnsendingType): Innsending.GenerellTekst {
        val tittel =
            when (innsendingType) {
                InnsendingSupplier.InnsendingType.DAGPENGER -> lookup<EnkelText>("soknad.header.tittel").text
                InnsendingSupplier.InnsendingType.GENERELL -> lookup<EnkelText>("pdf.generell-innsending.hovedoverskrift").text
            }
        return Innsending.GenerellTekst(
            hovedOverskrift = tittel,
            tittel = tittel,
            svar = (lookup<EnkelText>("pdf.svar")).text,
            datoSendt = (lookup<EnkelText>("pdf.datosendt")).text,
            fnr = (lookup<EnkelText>("pdf.fnr")).text,
        )
    }

    internal fun generellTekstEttersending(): Innsending.GenerellTekst {
        val tittel = lookup<EnkelText>("ettersending.tittel").text
        return Innsending.GenerellTekst(
            hovedOverskrift = tittel,
            tittel = tittel,
            svar = lookup<EnkelText>("pdf.svar").text,
            datoSendt = lookup<EnkelText>("pdf.datosendt").text,
            fnr = lookup<EnkelText>("pdf.fnr").text,
        )
    }

    internal fun pdfaMetaTags(): Innsending.PdfAMetaTagger =
        objectMapper.readTree(tekstJson).apptekster().let {
            Innsending.PdfAMetaTagger(
                description = (lookup<EnkelText>("pdfa.description")).text,
                subject = (lookup<EnkelText>("pdfa.subject")).text,
                author = (lookup<EnkelText>("pdfa.author")).text,
            )
        }

    sealed class TekstObjekt(
        val textId: String,
        val description: RawHtmlString?,
        val helpText: HelpText?,
    ) {
        class FaktaTekstObjekt(
            val unit: String? = null,
            val text: String,
            textId: String,
            description: RawHtmlString? = null,
            helpText: HelpText? = null,
        ) : TekstObjekt(textId, description, helpText)

        class SeksjonTekstObjekt(
            val title: String,
            textId: String,
            description: RawHtmlString? = null,
            helpText: HelpText? = null,
        ) : TekstObjekt(textId, description, helpText)

        class SvaralternativTekstObjekt(
            val text: String,
            val alertText: AlertText?,
            textId: String,
        ) : TekstObjekt(textId, null, null)

        class EnkelText(
            textId: String,
            val text: String,
        ) : TekstObjekt(textId, null, null)

        class AlertText(
            val title: String?,
            val type: String,
            val body: RawHtmlString?,
        ) {
            init {
                if (!listOf("info", "warning", "error", "succes").contains(type)) {
                    throw IllegalArgumentException("Ukjent type for alertText $type")
                }
            }
        }

        class DokumentkravTekstObjekt(
            textId: String,
            val title: String,
            description: RawHtmlString? = null,
            helpText: HelpText? = null,
        ) : TekstObjekt(textId, description, helpText)

        class HelpText(
            val title: String?,
            val body: RawHtmlString?,
        )
    }
}

private fun JsonNode.asRawHtmlString(): RawHtmlString? =
    when {
        this is TextNode -> this.asText()
        this is ArrayNode -> this.toList().joinToString(separator = "") { it.asText() }
        isNull -> null
        else -> throw UgyldigHtmlError(this.toPrettyString())
    }.let { RawHtmlString.nyEllerNull(it) }

class RawHtmlString private constructor(
    htmlFraSanity: String,
) {
    val html: String = Jsoup.clean(htmlFraSanity, tilatteTaggerOgAttributter)

    companion object {
        internal fun nyEllerNull(htmlString: String?): RawHtmlString? =
            when {
                htmlString.isNullOrEmpty() -> null
                else -> RawHtmlString(htmlString)
            }

        private val tilatteTaggerOgAttributter = Safelist.relaxed().removeTags("img", "br")
    }
}

private fun JsonNode.hjelpetekst(): HelpText? =
    get("helpText")?.let {
        HelpText(it.get("title")?.asText(), it.get("body")?.asRawHtmlString())
    }

private fun JsonNode.seksjoner() = this["sanityTexts"]["seksjoner"]

private fun JsonNode.svaralternativer() = this["sanityTexts"]["svaralternativer"]

private fun JsonNode.dokumentkravSvaralternativer() = this["sanityTexts"]["dokumentkravSvar"] ?: emptyList<JsonNode>()

private fun JsonNode.dokumentkrav() = this["sanityTexts"]["dokumentkrav"]

private fun JsonNode.fakta() = this["sanityTexts"]["fakta"]

private fun JsonNode.apptekster(): JsonNode = this["sanityTexts"]["apptekster"]

class UgyldigHtmlError(
    htmlString: String,
) : IllegalArgumentException("Mottok ugyldig HTML: $htmlString")
