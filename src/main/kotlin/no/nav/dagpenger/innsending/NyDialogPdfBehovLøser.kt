package no.nav.dagpenger.innsending

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.innsending.LagretDokument.Companion.behovSvar
import no.nav.dagpenger.innsending.html.Innsending
import no.nav.dagpenger.innsending.html.InnsendingSupplier
import no.nav.dagpenger.innsending.pdf.PdfLagring
import no.nav.dagpenger.innsending.serder.dokumentSpråk
import no.nav.dagpenger.innsending.serder.ident
import no.nav.dagpenger.innsending.serder.innsendtTidspunkt
import no.nav.dagpenger.innsending.serder.søknadUuid
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class NyDialogPdfBehovLøser(
    rapidsConnection: RapidsConnection,
    private val pdfLagring: PdfLagring,
    private val innsendingSupplier: InnsendingSupplier
) : River.PacketListener {
    companion object {
        private val logg = KotlinLogging.logger {}
        const val BEHOV = "ArkiverbarSøknad"
        private fun JsonMessage.innsendingType(): InnsendingSupplier.InnsendingType {
            return when (this["skjemakode"].asText()) {
                "GENERELL_INNSENDING" -> InnsendingSupplier.InnsendingType.GENERELL
                else -> InnsendingSupplier.InnsendingType.DAGPENGER
            }
        }
    }

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "behov") }
            validate { it.demandAll("@behov", listOf(BEHOV)) }
            validate { it.rejectKey("@løsning") }
            validate { it.requireKey("søknad_uuid", "ident", "innsendtTidspunkt", "skjemakode") }
            validate { it.requireValue("type", "NY_DIALOG") }
            validate { it.interestedIn("dokument_språk") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val soknadId = packet.søknadUuid()
        val ident = packet.ident()
        withLoggingContext("søknadId" to soknadId.toString()) {
            try {

                runBlocking(MDCContext()) {
                    val innsendingType = packet.innsendingType()
                    logg.info("Mottok behov for PDF av søknad. Skjemakode: $innsendingType ")
                    innsendingSupplier.hentSoknad(
                        soknadId,
                        packet.dokumentSpråk(),
                        innsendingType
                    )
                        .apply {
                            infoBlokk =
                                Innsending.InfoBlokk(
                                    fødselsnummer = ident,
                                    innsendtTidspunkt = packet.innsendtTidspunkt()
                                )
                        }
                        .let { lagArkiverbartDokument(it) }
                        .let { dokumenter ->
                            pdfLagring.lagrePdf(
                                søknadUUid = soknadId.toString(),
                                arkiverbartDokument = dokumenter,
                                fnr = ident
                            ).let {
                                with(it.behovSvar()) {
                                    packet["@løsning"] = mapOf(BEHOV to this)
                                }
                            }
                        }
                    with(packet.toJson()) {
                        context.publish(this)
                        sikkerlogg.info { "Sender løsning for $BEHOV: $this" }
                    }
                }
            } catch (e: Exception) {
                logg.error(e) { "Kunne ikke lage PDF for søknad med id: $soknadId" }
                throw e
            }
        }
    }
}
