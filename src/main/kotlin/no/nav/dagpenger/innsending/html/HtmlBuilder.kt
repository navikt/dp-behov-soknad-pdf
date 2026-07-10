package no.nav.dagpenger.innsending.html

import kotlinx.html.BODY
import kotlinx.html.HEAD
import kotlinx.html.body
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.lang
import kotlinx.html.stream.createHTML

internal object HtmlBuilder {
    fun lagHtml(
        språk: String,
        head: HEAD.() -> Unit = {},
        body: BODY.() -> Unit = {},
    ): String {
        return createHTML(prettyPrint = false, xhtmlCompatible = true)
            .html {
                lang = språk
                head(head)
                body(null, body)
            }.replace("&nbsp;", " ")
            .replace("\u001d", "") // Group separator character
            .replace("\u001c", "") // File separator character
    }
}
