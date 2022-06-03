package no.nav.dagpenger.soknad.html

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal data class InnsendtSøknad(
    val seksjoner: List<Seksjon>,
    val metaInfo: MetaInfo
) {

    lateinit var infoBlokk: InfoBlokk

    object PdfAMetaTagger {
        const val description: String = "Søknad om dagpenger"
        const val subject: String = "Dagpenger"
        const val author: String = "NAV"
    }

    data class Seksjon(
        val overskrift: String,
        val beskrivelse: String? = null,
        val hjelpetekst: Hjelpetekst? = null,
        val spmSvar: List<SporsmalSvar>
    )

    data class SporsmalSvar(
        val sporsmal: String,
        val svar: Svar,
        val beskrivelse: String? = null,
        val hjelpetekst: Hjelpetekst? = null,
        val oppfølgingspørmål: List<SpørmsålOgSvarGruppe> = emptyList(),
    )

    data class SpørmsålOgSvarGruppe(val spørsmålOgSvar: List<SporsmalSvar>)

    sealed class Svar
    data class EnkeltSvar(val tekst: String) : Svar()
    data class FlerSvar(val alternativ: List<SvarAlternativ>) : Svar()
    data class SvarAlternativ(val tekst: String, val tilleggsinformasjon: InfoTekst?)
    object IngenSvar : Svar()

    data class Hjelpetekst(val tekst: String, val tittel: String? = null)
    data class InfoTekst(val tittel: String?, val tekst: String, val type: Infotype)

    data class MetaInfo(
        val språk: SøknadSpråk = SøknadSpråk.BOKMÅL,
        val hovedOverskrift: String = språk.hovedOverskrift,
        val tittel: String = språk.tittel,
    )

    data class InfoBlokk(val fødselsnummer: String, val innsendtTidspunkt: LocalDateTime) {
        val datoSendt = innsendtTidspunkt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
    }

    enum class Infotype() {
//        "info" | "warning" | "error" | "success";
        INFORMASJON, ADVARSEL, FEIL
    }

    enum class SøknadSpråk(
        val langAtributt: String,
        val svar: String,
        val fødselsnummer: String,
        val datoSendt: String,
        val hovedOverskrift: String,
        val tittel: String,
        val boolean: (Boolean) -> String
    ) {
        BOKMÅL(
            "no",
            "Svar",
            "Fødselsnummer",
            "Dato sendt",
            "Søknad om dagpenger",
            "Søknad om dagpenger",
            { b: Boolean ->
                if (b) "Ja" else "Nei"
            }
        ),
        ENGELSK(
            "en",
            "Answer",
            "Social security number",
            "Date sent",
            "TODO: hovedoverskrift engelsk",
            "TODO: hovedoverskrift engelsk",
            { b: Boolean ->
                if (b) "Yes" else "No"
            }
        )
    }
}
