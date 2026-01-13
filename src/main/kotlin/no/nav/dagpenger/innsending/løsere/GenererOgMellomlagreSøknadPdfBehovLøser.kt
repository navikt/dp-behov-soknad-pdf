package no.nav.dagpenger.innsending.løsere

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
import no.nav.dagpenger.innsending.ArkiverbartDokument
import no.nav.dagpenger.innsending.LagretDokument.Companion.behovSvar
import no.nav.dagpenger.innsending.pdf.PdfBuilder
import no.nav.dagpenger.innsending.pdf.PdfLagring
import no.nav.dagpenger.innsending.serder.asUUID
import no.nav.dagpenger.innsending.serder.ident

internal class GenererOgMellomlagreSøknadPdfBehovLøser(
    rapidsConnection: RapidsConnection,
    private val pdfLagring: PdfLagring,
) : River.PacketListener {
    companion object {
        private val logg = KotlinLogging.logger {}
        private val sikkerLogg = KotlinLogging.logger("tjenestekall.${this::class.java.simpleName}")
        const val BEHOV = "generer_og_mellomlagre_søknad_pdf"
        private val `søknadIdLogAndSkipSet` =
            setOf(
                "cbcd53f8-cc00-4bf0-ae44-27656043ddaa",
            )
    }

    init {
        River(rapidsConnection)
            .apply {
                precondition { it.requireValue("@event_name", "behov") }
                precondition { it.requireAllOrAny("@behov", listOf(BEHOV)) }
                precondition { it.forbid("@løsning") }
                validate { it.requireKey("søknadId", "ident", "bruttoPayload", "nettoPayload") }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val søknadId = packet["søknadId"].asUUID()
        val ident = packet.ident()
        val nettoPayload = packet["nettoPayload"].asText()
        val bruttoPayload = packet["bruttoPayload"].asText()
        withLoggingContext("søknadId" to søknadId.toString()) {
            try {
                runBlocking(MDCContext()) {
                    logg.info("Mottok behov for generering av PDF for søknad $søknadId")

                    if (søknadIdLogAndSkipSet.contains(søknadId.toString())) {
                        logg.info { "$søknadId er i logAndSkipSet, så logger og skipper denne" }
                        sikkerLogg.info { "NettoPayload $søknadId: $nettoPayload" }
                        sikkerLogg.info { "BruttoPayload $søknadId: $bruttoPayload" }
                        return@runBlocking
                    }

                    val nettoPdf = PdfBuilder.lagPdf(nettoPayload)
                    val bruttoPdf = PdfBuilder.lagPdf(bruttoPayload)
                    listOf(
                        ArkiverbartDokument.netto(nettoPdf),
                        ArkiverbartDokument.brutto(bruttoPdf),
                    ).let { arkivbareDokumenter ->
                        pdfLagring
                            .lagrePdf(
                                søknadUUid = søknadId.toString(),
                                arkiverbartDokument = arkivbareDokumenter,
                                fnr = ident,
                            ).let {
                                with(it.behovSvar()) {
                                    packet["@løsning"] = mapOf(BEHOV to this)
                                }
                            }
                    }
                    with(packet.toJson()) {
                        context.publish(this)
                        logg.info { "Sendte løsning for $BEHOV for søknad $søknadId" }
                        sikkerLogg.info { "Sendte løsning for $BEHOV for søknad $søknadId: $this" }
                    }
                }
            } catch (e: Exception) {
                logg.error(e) { "Kunne ikke lage PDF for søknad med id: $søknadId" }
                throw e
            }
        }
    }
}
