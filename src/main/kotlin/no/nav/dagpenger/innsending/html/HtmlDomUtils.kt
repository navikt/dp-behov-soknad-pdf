package no.nav.dagpenger.innsending.html

import kotlinx.html.DIV
import kotlinx.html.HEAD
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.i
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.meta
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.ul
import kotlinx.html.unsafe
import mu.KotlinLogging
import no.nav.dagpenger.innsending.html.Innsending.GenerellTekst
import no.nav.dagpenger.innsending.html.Innsending.SporsmalSvar

private val logg = KotlinLogging.logger {}

internal fun HEAD.pdfaMetaTags(innsending: Innsending) {
    with(innsending.pdfAMetaTagger) {
        meta {
            name = "description"
            content = description
        }
        meta {
            name = "subject"
            content = subject
        }

        meta {
            name = "author"
            content = author
        }
    }
}

internal fun HEAD.bookmarks(innsending: Innsending) {
// TODO: Språktilpassning på statiske bokmerker
    val seksjonBokmerker =
        innsending.seksjoner.map {
            """<bookmark name = "${it.overskrift}" href="#${seksjonId(it.overskrift)}"></bookmark>"""
        }
    val dokumentasjonBookmark =
        when {
            innsending.dokumentasjonskrav.isNotEmpty() -> """<bookmark name = "Dokumentasjon" href="#Dokumentasjon"></bookmark>"""
            else -> null
        }
    val bokmerker = listOfNotNull(seksjonBokmerker, dokumentasjonBookmark).joinToString("")

    unsafe {
        //language=HTML
        raw(
            """
            <bookmarks>
                <bookmark name="${innsending.generellTekst.hovedOverskrift}" href="#hovedoverskrift"></bookmark>
                <bookmark name="Info om søknad" href="#infoblokk"></bookmark>
                $bokmerker
            </bookmarks>
            """.trimIndent(),
        )
    }
}

internal fun DIV.boldSpanP(
    boldTekst: String,
    vanligTekst: String,
) {
    p {
        span(classes = "boldSpan") { +"$boldTekst: " }
        +vanligTekst
    }
}

internal fun DIV.begrunnelse(begrunnelse: String) {
    p {
        +"Begrunnelse: "
        i(classes = "kursiv") { +begrunnelse }
    }
}

internal fun DIV.valgsvar(
    boldTekst: String,
    svar: Innsending.ValgSvar,
    brutto: Boolean,
) {
    p {
        span(classes = "boldSpan") { +"$boldTekst: " }
        if (svar.alternativ.size == 1) {
            +svar.alternativ.first().tekst
        } else if (svar.alternativ.size > 1) {
            ul {
                svar.alternativ.forEach {
                    li { +it.tekst }
                }
            }
        }
    }
    if (svar.alternativ.isNotEmpty() && brutto) {
        div {
            svar.alternativ.forEach { svaralternativ ->
                svaralternativ.tilleggsinformasjon?.also { info ->
                    div(classes = "hjelpetekst") {
                        h3 { +tilleggsinformasjonOverskrift(info) }
                        info.unsafeHtmlBody?.let { unsafe { +info.unsafeHtmlBody.kode } }
                    }
                }
            }
        }
    }
}

private fun DIV.dokumentasjonKravBrutto(dokumentKrav: Innsending.DokumentKrav) {
    try {
        dokumentKrav.beskrivelse?.also { unsafe { +dokumentKrav.beskrivelse.medCssKlasse("infotekst") } }
    } catch (error: Exception) {
        throw error
    }
    dokumentKrav.hjelpetekst?.also {
        div(classes = "hjelpetekst") {
            dokumentKrav.hjelpetekst.tittel?.also { tittel -> h3 { +tittel } }
            dokumentKrav.hjelpetekst.unsafeHtmlBody?.let { unsafe { +dokumentKrav.hjelpetekst.unsafeHtmlBody.kode } }
        }
    }
}

private fun Innsending.DokumentKrav.tittel(): String =
    when (this.kravSvar) {
        null -> this.navn
        else -> {
            "$navn ($kravSvar)"
        }
    }

internal fun DIV.dokumentasjonKrav(
    dokumentKrav: List<Innsending.DokumentKrav>,
    valg: Innsending.DokumentKrav.Valg,
    brutto: Boolean,
) {
    when (valg) {
        Innsending.DokumentKrav.Valg.SEND_NAA -> {
            val innsendts = dokumentKrav.filterIsInstance<Innsending.Innsendt>()
            if (innsendts.isNotEmpty()) {
                p { +"Du har lagt ved følgende dokumentasjon: " }
                ul(classes = "dokumentasjonkrav") {
                    innsendts.forEach { dokumentKrav ->
                        li(classes = "listSpacing") {
                            p { +(dokumentKrav.tittel()) }
                            if (brutto) this@dokumentasjonKrav.dokumentasjonKravBrutto(dokumentKrav)
                        }
                    }
                }
            }
        }

        Innsending.DokumentKrav.Valg.SEND_SENERE -> {
            val innsendts = dokumentKrav.filterIsInstance<Innsending.IkkeInnsendtNå>().filter { it.valg == valg }
            dokumentKrav(innsendts, "Du har sagt at du skal sende følgende dokumentasjon:", brutto)
        }

        Innsending.DokumentKrav.Valg.SENDT_TIDLIGERE -> {
            val innsendts = dokumentKrav.filterIsInstance<Innsending.IkkeInnsendtNå>().filter { it.valg == valg }
            dokumentKrav(innsendts, "Du har sagt at du tidligere har sendt inn følgende dokumentasjon:", brutto)
        }

        Innsending.DokumentKrav.Valg.SENDER_IKKE -> {
            val innsendts = dokumentKrav.filterIsInstance<Innsending.IkkeInnsendtNå>().filter { it.valg == valg }
            dokumentKrav(innsendts, "Du har sagt at du ikke sender følgende dokumentasjon:", brutto)
        }

        Innsending.DokumentKrav.Valg.ANDRE_SENDER -> {
            val innsendts = dokumentKrav.filterIsInstance<Innsending.IkkeInnsendtNå>().filter { it.valg == valg }
            dokumentKrav(innsendts, "Du har sagt at andre skal sende følgende dokumentasjon:", brutto)
        }
    }
}

private fun DIV.dokumentKrav(
    innsendts: List<Innsending.IkkeInnsendtNå>,
    beskrivelse: String,
    brutto: Boolean,
) {
    if (innsendts.isNotEmpty()) {
        p { +beskrivelse }
        ul(classes = "dokumentasjonkrav") {
            innsendts.forEach { dokumentKrav ->
                li(classes = "listSpacing") {
                    p { +dokumentKrav.tittel() }
                    div {
                        begrunnelse(dokumentKrav.begrunnelse)
                    }
                    if (brutto) this@dokumentKrav.dokumentasjonKravBrutto(dokumentKrav)
                }
            }
        }
    }
}

private fun tilleggsinformasjonOverskrift(info: Innsending.InfoTekst): String {
    var overskrift =
        info.type.name
            .lowercase()
            .replaceFirstChar { it.uppercase() }
    if (info.tittel != null) {
        overskrift += ": ${info.tittel}"
    }
    return overskrift
}

private fun DIV.svar(
    tekst: GenerellTekst,
    svar: Innsending.Svar,
    brutto: Boolean = false,
) {
    when (svar) {
        is Innsending.EnkeltSvar -> boldSpanP(tekst.svar, svar.tekst)
        is Innsending.ValgSvar -> valgsvar(tekst.svar, svar, brutto)
        Innsending.IngenSvar -> {}
    }
}

internal fun DIV.nettoSeksjon(
    seksjon: Innsending.Seksjon,
    tekst: GenerellTekst,
) {
    id = seksjonId(seksjon.overskrift)
    h2 { +seksjon.overskrift }
    seksjon.spmSvar.forEach { nettoSpørsmål(it, tekst) }
}

private fun DIV.nettoSpørsmål(
    spmSvar: SporsmalSvar,
    tekst: GenerellTekst,
) {
    div {
        h3 { +spmSvar.sporsmal }
        svar(tekst, spmSvar.svar)
        spmSvar.oppfølgingspørmål.forEach { oppfølging ->
            div(classes = "gruppering") {
                oppfølging.spørsmålOgSvar.forEach {
                    nettoSpørsmål(it, tekst)
                }
            }
        }
    }
}

internal fun DIV.bruttoSeksjon(
    seksjon: Innsending.Seksjon,
    tekst: GenerellTekst,
) {
    id = seksjonId(seksjon.overskrift)
    h2 { +seksjon.overskrift }
    try {
        seksjon.beskrivelse?.also { unsafe { +seksjon.beskrivelse.medCssKlasse("infotekst") } }
    } catch (error: Exception) {
        throw error
    }
    seksjon.hjelpetekst?.also {
        div(classes = "hjelpetekst") {
            seksjon.hjelpetekst.tittel?.also { tittel -> h3 { +tittel } }
            seksjon.hjelpetekst.unsafeHtmlBody?.let { unsafe { +seksjon.hjelpetekst.unsafeHtmlBody.kode } }
        }
    }
    seksjon.spmSvar.forEach {
        bruttoSpørsmål(it, tekst)
    }
}

private fun DIV.bruttoSpørsmål(
    spmSvar: SporsmalSvar,
    tekst: GenerellTekst,
) {
    div {
        h3 { +spmSvar.sporsmal }
        // todo
        try {
            spmSvar.beskrivelse?.also { unsafe { +spmSvar.beskrivelse.medCssKlasse("infotekst") } }
        } catch (e: Exception) {
            logg.error { "Feil i generering av bruttoSpm.beskrivelse. Beskrivelse:${spmSvar.beskrivelse}. Error: $e" }
        }
        spmSvar.hjelpetekst?.also {
            div(classes = "hjelpetekst") {
                spmSvar.hjelpetekst.tittel?.also { tittel -> h3 { +tittel } }
                spmSvar.hjelpetekst.unsafeHtmlBody?.let {
                    unsafe { +spmSvar.hjelpetekst.unsafeHtmlBody.kode }
                }
            }
        }
        svar(tekst, spmSvar.svar, true)
        spmSvar.oppfølgingspørmål.forEach { oppfølging ->
            oppfølging.spørsmålOgSvar.forEach {
                bruttoSpørsmål(it, tekst)
            }
        }
    }
}

private fun seksjonId(overskrift: String) = "seksjon-${overskrift.replace(" ", "-").lowercase()}"
