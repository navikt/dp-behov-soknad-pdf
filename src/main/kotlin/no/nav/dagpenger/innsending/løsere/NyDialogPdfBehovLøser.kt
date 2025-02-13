package no.nav.dagpenger.innsending.løsere

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.innsending.LagretDokument.Companion.behovSvar
import no.nav.dagpenger.innsending.html.InnsendingSupplier
import no.nav.dagpenger.innsending.lagArkiverbartDokument
import no.nav.dagpenger.innsending.pdf.PdfLagring
import no.nav.dagpenger.innsending.serder.dokumentSpråk
import no.nav.dagpenger.innsending.serder.ident
import no.nav.dagpenger.innsending.serder.innsendtTidspunkt
import no.nav.dagpenger.innsending.serder.søknadUuid

private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class NyDialogPdfBehovLøser(
    rapidsConnection: RapidsConnection,
    private val pdfLagring: PdfLagring,
    private val innsendingSupplier: InnsendingSupplier,
) : River.PacketListener {
    companion object {
        private val logg = KotlinLogging.logger {}
        const val BEHOV = "ArkiverbarSøknad"

        private fun JsonMessage.innsendingType(): InnsendingSupplier.InnsendingType =
            when (this["skjemakode"].asText()) {
                "GENERELL_INNSENDING" -> InnsendingSupplier.InnsendingType.GENERELL
                else -> InnsendingSupplier.InnsendingType.DAGPENGER
            }
    }

    init {
        River(rapidsConnection)
            .apply {
                precondition { it.requireValue("@event_name", "behov") }
                precondition { it.requireAllOrAny("@behov", listOf(BEHOV)) }
                precondition { it.forbid("@løsning") }
                validate { it.requireKey("søknad_uuid", "ident", "innsendtTidspunkt", "skjemakode") }
                validate { it.requireValue("type", "NY_DIALOG") }
                validate { it.interestedIn("dokument_språk") }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val soknadId = packet.søknadUuid()
        val ident = packet.ident()
        val innsendtTidspunkt = packet.innsendtTidspunkt()
        withLoggingContext("søknadId" to soknadId.toString()) {
            try {
                runBlocking(MDCContext()) {
                    val innsendingType = packet.innsendingType()
                    logg.info("Mottok behov for PDF av søknad. Skjemakode: $innsendingType ")
                    innsendingSupplier
                        .hentSoknad(
                            soknadId,
                            ident,
                            innsendtTidspunkt,
                            packet.dokumentSpråk(),
                            innsendingType,
                        ).let { lagArkiverbartDokument(it) }
                        .let { dokumenter ->
                            pdfLagring
                                .lagrePdf(
                                    søknadUUid = soknadId.toString(),
                                    arkiverbartDokument = dokumenter,
                                    fnr = ident,
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
