package no.nav.dagpenger.innsending.html

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal data class Innsending(
    val seksjoner: List<Seksjon>,
    val generellTekst: GenerellTekst,
    val språk: InnsendingsSpråk,
    val pdfAMetaTagger: PdfAMetaTagger,
    val dokumentasjonskrav: List<DokumentKrav>,
    val type: InnsendingSupplier.InnsendingType = InnsendingSupplier.InnsendingType.DAGPENGER,
) {
    lateinit var infoBlokk: InfoBlokk

    open class PdfAMetaTagger(
        val description: String,
        val subject: String,
        val author: String,
    )

    object DefaultPdfAMetaTagger :
        PdfAMetaTagger(description = "Søknad om dagpenger", subject = "Dagpenger", author = "NAV Dagpenger")

    data class Seksjon(
        val overskrift: String,
        val beskrivelse: UnsafeHtml? = null,
        val hjelpetekst: Hjelpetekst? = null,
        val spmSvar: List<SporsmalSvar>,
    )

    data class SporsmalSvar(
        val sporsmal: String,
        val svar: Svar,
        val beskrivelse: UnsafeHtml? = null,
        val hjelpetekst: Hjelpetekst? = null,
        val oppfølgingspørmål: List<SpørmsålOgSvarGruppe> = emptyList(),
    )

    data class SpørmsålOgSvarGruppe(
        val spørsmålOgSvar: List<SporsmalSvar>,
    )

    sealed class Svar

    data class EnkeltSvar(
        var tekst: String,
    ) : Svar() {
        init {
            tekst = tekst.replace("\u0002", " ")
        }
    }

    data class ValgSvar(
        val alternativ: List<SvarAlternativ>,
    ) : Svar()

    data class SvarAlternativ(
        val tekst: String,
        val tilleggsinformasjon: InfoTekst?,
    )

    object IngenSvar : Svar()

    class Hjelpetekst private constructor(
        val unsafeHtmlBody: UnsafeHtml?,
        val tittel: String? = null,
    ) {
        companion object {
            fun nyEllerNull(
                unsafeHtmlBody: UnsafeHtml? = null,
                tittel: String? = null,
            ) = when {
                tittel.isNullOrEmpty() && unsafeHtmlBody?.kode.isNullOrEmpty() -> null
                else -> Hjelpetekst(unsafeHtmlBody, tittel)
            }
        }
    }

    class InfoTekst private constructor(
        val tittel: String?,
        val unsafeHtmlBody: UnsafeHtml?,
        val type: Infotype,
    ) {
        companion object {
            fun nyEllerNull(
                tittel: String? = null,
                unsafeHtmlBody: UnsafeHtml? = null,
                type: Infotype,
            ) = when {
                tittel.isNullOrEmpty() && unsafeHtmlBody?.kode.isNullOrEmpty() -> null
                else -> InfoTekst(tittel, unsafeHtmlBody, type)
            }
        }
    }

    data class GenerellTekst(
        val hovedOverskrift: String,
        val tittel: String,
        val svar: String,
        val datoSendt: String,
        val fnr: String,
    )

    data class InfoBlokk(
        val fødselsnummer: String,
        val navn: String?,
        val adresse: String,
        val innsendtTidspunkt: ZonedDateTime,
    ) {
        companion object {
            private val datetimeFormatter: DateTimeFormatter =
                DateTimeFormatter
                    .ofLocalizedDateTime(
                        FormatStyle.LONG,
                        FormatStyle.SHORT,
                    ).withLocale(Locale.of("no", "NO"))
        }

        val datoSendt: String = innsendtTidspunkt.format(datetimeFormatter)
    }

    enum class Infotype {
        INFORMASJON,
        ADVARSEL,
        FEIL,
        ;

        companion object {
            fun fraSanityJson(typenøkkel: String) =
                when (typenøkkel) {
                    "info" -> INFORMASJON
                    "error" -> FEIL
                    "warning" -> ADVARSEL
                    "success" -> null
                    else -> {
                        throw IllegalArgumentException("ukjent alerttekst type $typenøkkel")
                    }
                }
        }
    }

    internal class UnsafeHtml(
        val kode: String,
    ) {
        // TODO: må fungere i arrays også
        // TODO: GTC Vet ikke helt hva some er hensikten med denne. Hack så det ikke tryner på Arrays
        fun medCssKlasse(klasse: String): String =
            when (kode.lowercase().startsWith("<p")) {
                true -> """<p class="$klasse"${kode.substringAfter("<p")}"""
                else -> kode
            }

        companion object {
            private fun String.leggTilPåHtmlPtag(kode: String): String = """<p class="$this"${kode.substringAfter("<p")}"""
        }

        override fun toString() = "UnsafeHtml($kode)"
    }

    enum class InnsendingsSpråk(
        val langAtributt: String,
    ) {
        BOKMÅL(
            "no",
        ),
        ENGELSK(
            "en",
        ),
    }

    internal sealed class DokumentKrav(
        val kravId: String,
        val kravSvar: String?,
        val navn: String,
        val beskrivelse: UnsafeHtml? = null,
        val hjelpetekst: Hjelpetekst? = null,
        val valg: Valg,
    ) {
        enum class Valg {
            SEND_NAA,
            SEND_SENERE,
            SENDT_TIDLIGERE,
            SENDER_IKKE,
            ANDRE_SENDER,
            ;

            companion object {
                fun fromJson(valg: String): Valg =
                    when (valg) {
                        "dokumentkrav.svar.send.naa" -> SEND_NAA
                        "dokumentkrav.svar.send.senere" -> SEND_SENERE
                        "dokumentkrav.svar.sendt.tidligere" -> SENDT_TIDLIGERE
                        "dokumentkrav.svar.sender.ikke" -> SENDER_IKKE
                        "dokumentkrav.svar.andre.sender" -> ANDRE_SENDER
                        else -> throw IllegalArgumentException("Kjenner ikke til svar: '$valg'")
                    }
            }
        }
    }

    class Innsendt(
        kravId: String,
        kravSvar: String?,
        navn: String,
        beskrivelse: UnsafeHtml?,
        hjelpetekst: Hjelpetekst?,
        valg: Valg,
    ) : DokumentKrav(kravId, kravSvar, navn, beskrivelse, hjelpetekst, valg)

    class IkkeInnsendtNå(
        kravId: String,
        kravSvar: String?,
        navn: String,
        val begrunnelse: String,
        beskrivelse: UnsafeHtml?,
        hjelpetekst: Hjelpetekst?,
        valg: Valg,
    ) : DokumentKrav(kravId, kravSvar, navn, beskrivelse, hjelpetekst, valg)
}
