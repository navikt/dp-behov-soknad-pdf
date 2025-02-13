package no.nav.dagpenger.innsending.løsere

import com.fasterxml.jackson.databind.node.ArrayNode
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
import no.nav.dagpenger.innsending.html.Innsending
import no.nav.dagpenger.innsending.lagArkiverbarEttersending
import no.nav.dagpenger.innsending.pdf.PdfLagring
import no.nav.dagpenger.innsending.serder.dokumentSpråk
import no.nav.dagpenger.innsending.serder.ident
import no.nav.dagpenger.innsending.serder.innsendtTidspunkt
import no.nav.dagpenger.innsending.serder.søknadUuid
import java.time.ZonedDateTime
import java.util.UUID

internal class EttersendingPdfBehovLøser(
    rapidsConnection: RapidsConnection,
    private val pdfLagring: PdfLagring,
    private val innsendingSupplier: suspend (
        soknadId: UUID,
        fnr: String,
        innsendtTidspunkt: ZonedDateTime,
        innsendingsSpråk: Innsending.InnsendingsSpråk,
        block: Innsending.() -> Innsending,
    ) -> Innsending,
) : River.PacketListener {
    companion object {
        private val logg = KotlinLogging.logger {}
        private val sikkerlogg = KotlinLogging.logger("tjenestekall")
        const val BEHOV = "ArkiverbarSøknad"
    }

    init {
        River(rapidsConnection)
            .apply {
                precondition { it.requireValue("@event_name", "behov") }
                precondition { it.requireAllOrAny("@behov", listOf(BEHOV)) }
                precondition { it.forbid("@løsning") }
                validate { it.requireKey("søknad_uuid", "ident", "innsendtTidspunkt", "dokumentasjonKravId") }
                validate { it.requireValue("type", "ETTERSENDING_TIL_DIALOG") }
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
        val innsendtDokumentajonsKravId = packet.innsendtDokumentajonsKravId()
        withLoggingContext("søknadId" to soknadId.toString()) {
            try {
                runBlocking(MDCContext()) {
                    logg.info("Mottok behov for PDF av ettersending")
                    innsendingSupplier(
                        soknadId,
                        ident,
                        innsendtTidspunkt,
                        packet.dokumentSpråk(),
                    ) { this.filtrerInnsendteDokumentasjonsKrav(innsendtDokumentajonsKravId) }
                        .let { lagArkiverbarEttersending(it) }
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

private fun JsonMessage.innsendtDokumentajonsKravId(): Set<String> {
    // todo errorhandling
    return (this["dokumentasjonKravId"] as ArrayNode).map { it.asText() }.toSet()
}

private fun Innsending.filtrerInnsendteDokumentasjonsKrav(innsendtDokumentajonsKravId: Set<String>): Innsending =
    this
        .copy(
            dokumentasjonskrav =
                this.dokumentasjonskrav.filter { dokumentKrav ->
                    dokumentKrav.kravId in innsendtDokumentajonsKravId
                },
        ).also {
            it.infoBlokk = this.infoBlokk
        }
