package no.nav.dagpenger.soknad.html

import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.dom.createHTMLDocument
import kotlinx.html.dom.serialize
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.id
import kotlinx.html.lang
import kotlinx.html.style
import kotlinx.html.title

internal object HtmlBuilder {
    fun lagHtml(htmlModell: HtmlModell): String {
        val språk = htmlModell.metaInfo.språk
        return createHTMLDocument().html {
            lang = språk.langAtributt
            head {
                title(htmlModell.metaInfo.tittel)
                pdfa(htmlModell.pdfAKrav)
                fontimports()
                bookmarks(htmlModell.seksjoner)
                style {
                    søknadPdfStyle()
                }
            }
            body {
                h1 {
                    id="hovedoverskrift"
                    +htmlModell.metaInfo.hovedOverskrift
                }
                div(classes = "infoblokk") {
                    id="infoblokk"
                    boldSpanP(boldTekst = språk.fødselsnummer, vanligTekst = htmlModell.infoBlokk.fødselsnummer)
                    boldSpanP(boldTekst = språk.datoSendt, vanligTekst = htmlModell.infoBlokk.fødselsnummer)
                }
                htmlModell.seksjoner.forEach { seksjon ->
                    div(classes = "seksjon") {
                        id= seksjonId(seksjon.overskrift)
                        h2 { +seksjon.overskrift }
                        seksjon.spmSvar.forEach { spmDiv(it, språk) }
                    }
                }
            }
        }.serialize(true).xhtmlCompliant()
    }
}
