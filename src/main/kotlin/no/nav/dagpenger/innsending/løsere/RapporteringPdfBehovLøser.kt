package no.nav.dagpenger.innsending.løsere

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeType
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.html.BODY
import kotlinx.html.DIV
import kotlinx.html.HEAD
import kotlinx.html.div
import kotlinx.html.title
import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.innsending.ArkiverbartDokument
import no.nav.dagpenger.innsending.LagretDokument.Companion.behovSvar
import no.nav.dagpenger.innsending.html.HtmlBuilder.lagHtml
import no.nav.dagpenger.innsending.html.søknadPdfStyle
import no.nav.dagpenger.innsending.pdf.PdfBuilder
import no.nav.dagpenger.innsending.pdf.PdfLagring
import no.nav.dagpenger.innsending.serder.ident
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

internal class RapporteringPdfBehovLøser(
    rapidsConnection: RapidsConnection,
    private val pdfLagring: PdfLagring,
) : River.PacketListener {
    companion object {
        private val logg = KotlinLogging.logger {}
        private val sikkerlogg = KotlinLogging.logger("tjenestekall")
        const val BEHOV = "MellomlagreRapportering"
    }

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "behov") }
            validate { it.demandAll("@behov", listOf(BEHOV)) }
            validate { it.rejectKey("@løsning") }
            validate { it.requireKey("ident") }
            validate {
                it.require(BEHOV) { behov ->
                    behov.required("periodeId")
                    behov.required("json")
                }
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val ident = packet.ident()
        val periodeId = packet[BEHOV]["periodeId"].asText()
        val json = packet[BEHOV]["json"].asText()
        val jsonTree = jacksonObjectMapper().readTree(json)

        withLoggingContext(
            "periodeId" to periodeId,
        ) {
            try {
                runBlocking(MDCContext()) {
                    logg.info("Mottok behov for PDF av rapportering")

                    // pre-tagen fungerer ikke, derfor må vi gjøre formatering selv
                    val html = lagHtml(
                        jsonTree["språk"].asText(),
                        head("Rapporteringperiode $periodeId"),
                        body(jsonTree),
                    )

                    pdfLagring.lagrePdf(
                        søknadUUid = periodeId,
                        arkiverbartDokument = listOf(ArkiverbartDokument.netto(PdfBuilder.lagPdf(html))),
                        fnr = ident,
                    ).let {
                        with(it.behovSvar()) {
                            packet["@løsning"] = mapOf(
                                BEHOV to this,
                            )
                        }
                    }

                    with(packet.toJson()) {
                        context.publish(this)
                        sikkerlogg.info { "Sender løsning for $BEHOV: $this" }
                    }
                }
            } catch (e: Exception) {
                logg.error(e) { "Kunne ikke lage PDF for periode med id: $periodeId" }
                throw e
            }
        }
    }

    private fun head(tittel: String): HEAD.() -> Unit {
        return {
            title(tittel)
            søknadPdfStyle()
        }
    }

    private fun body(json: JsonNode): BODY.() -> Unit {
        return {
            div(null, iterate(json, ""))
        }
    }

    private fun iterate(json: JsonNode, indent: String): DIV.() -> Unit {
        return {
            val iterator = json.fields()
            while (iterator.hasNext()) {
                val item = iterator.next()

                if (item.value.nodeType == JsonNodeType.OBJECT) {
                    div { +"$indent ${item.key}: {" }
                    div(null, iterate(item.value, "  $indent"))
                    div { +"$indent }" }
                } else {
                    div {
                        +"$indent ${item.key}: ${item.value.asText()}"
                    }
                }
            }
        }
    }
}
