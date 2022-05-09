package no.nav.dagpenger.soknad.html

import kotlinx.html.DIV
import kotlinx.html.HEAD
import kotlinx.html.div
import kotlinx.html.h3
import kotlinx.html.meta
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.unsafe
import no.nav.dagpenger.soknad.html.HtmlModell.PdfAKrav
import no.nav.dagpenger.soknad.html.HtmlModell.SporsmalSvar
import no.nav.dagpenger.soknad.html.HtmlModell.SøknadSpråk

internal fun String.xhtmlCompliant() = this
    .replace("&oslash;", "ø")
    .replace("&aring;", "å")
    .replace("&aelig;", "æ")
    .replace(
        Regex("(?<=<(meta|META)[a-zA-ZæøåÆØÅ=\\\"\\/\\s\\-\\.\\;0-9]{1,1000})>"),
        replacement = "/>"
    )
    .replace(
        Regex(pattern = "(?<=<(link[\\sa-zA-ZæøåÆØÅ=\\\"\\:\\/\\.0-9\\?\\+\\,@\\;\\&]{1,1000}))>"),
        replacement = "/>"
    )

internal fun HEAD.pdfa(pdfAKrav: PdfAKrav) {
    meta {
        name = "description"
        content = pdfAKrav.description
    }
    meta {
        name = "subject"
        content = pdfAKrav.subject
    }

    meta {
        name = "author"
        content = pdfAKrav.author
    }
}

internal fun HEAD.bookmarks(htmlModell: HtmlModell) {
    unsafe {
        //language=HTML
        raw(
            """
                <bookmarks>
                    <bookmark name="Søknad om dagpenger" href="#hovedoverskrift"></bookmark>
                    <bookmark name="Info om søknad" href="#infoblokk"></bookmark>
                </bookmarks>
            """.trimIndent()
        )
    }
}

internal fun DIV.boldSpanP(boldTekst: String, vanligTekst: String, divider: String = ":") {
    p {
        span(classes = "boldSpan") { +"$boldTekst: " }
        +vanligTekst
    }
}

internal fun DIV.spmDiv(spmSvar: SporsmalSvar, språk: SøknadSpråk) {
    div {
        h3 { +spmSvar.sporsmal }
        if (spmSvar.infotekst != null) {
            p(classes = "infotekst") { +spmSvar.infotekst }
        }
        if (spmSvar.hjelpeTekst != null) {
            p(classes = "hjelpetekst") {
                +spmSvar.hjelpeTekst
            }
        }
        boldSpanP(språk.svar, spmSvar.svar)
        spmSvar.oppfølgingspørmål?.forEach { oppfølging ->
            spmDiv(oppfølging, språk)
        }
    }
}
