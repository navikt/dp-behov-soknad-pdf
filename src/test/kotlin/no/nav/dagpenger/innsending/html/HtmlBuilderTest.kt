package no.nav.dagpenger.innsending.html

import kotlinx.html.BODY
import kotlinx.html.HEAD
import kotlinx.html.div
import kotlinx.html.title
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HtmlBuilderTest {
    @Test
    fun `lager html`() {
        val head: HEAD.() -> Unit = {
            title("Test tittel")
        }

        val body: BODY.() -> Unit = {
            div { +"Test div" }
        }

        val html = HtmlBuilder.lagHtml("no-NB", head, body)

        assertEquals(
            "<html lang=\"no-NB\"><head><title>Test tittel</title></head><body><div>Test div</div></body></html>",
            html,
        )
    }
}
