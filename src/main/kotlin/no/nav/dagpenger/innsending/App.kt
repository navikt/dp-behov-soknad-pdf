package no.nav.dagpenger.innsending

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.dagpenger.innsending.løsere.GenererOgMellomlagreSøknadPdfBehovLøser
import no.nav.dagpenger.innsending.løsere.RapporteringPdfBehovLøser
import no.nav.dagpenger.innsending.pdf.PdfLagring
import no.nav.helse.rapids_rivers.RapidApplication

fun main() {
    App.start()
}

internal object App : RapidsConnection.StatusListener {
    private val rapidsConnection = RapidApplication.create(Configuration.config)

    init {
        rapidsConnection.register(this)
        RapporteringPdfBehovLøser(
            rapidsConnection = rapidsConnection,
            pdfLagring =
                PdfLagring(
                    baseUrl = Configuration.dpMellomlagringBaseUrl,
                    tokenSupplier = Configuration.mellomlagringTokenSupplier,
                ),
        )
        GenererOgMellomlagreSøknadPdfBehovLøser(
            rapidsConnection = rapidsConnection,
            pdfLagring =
                PdfLagring(
                    baseUrl = Configuration.dpMellomlagringBaseUrl,
                    tokenSupplier = Configuration.mellomlagringTokenSupplier,
                ),
        )
    }

    fun start() = rapidsConnection.start()
}
