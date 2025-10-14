package no.nav.dagpenger.innsending.serder

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import no.nav.dagpenger.innsending.html.Innsending
import java.time.ZonedDateTime
import java.util.UUID

internal fun JsonMessage.dokumentSpråk(): Innsending.InnsendingsSpråk =
    when (this["dokument_språk"].asText()) {
        "en" -> Innsending.InnsendingsSpråk.ENGELSK
        "nb" -> Innsending.InnsendingsSpråk.BOKMÅL
        else -> Innsending.InnsendingsSpråk.BOKMÅL
    }

internal fun JsonMessage.ident() = this["ident"].asText()

internal fun JsonMessage.innsendtTidspunkt(): ZonedDateTime = this["innsendtTidspunkt"].asZonedDateTime()

internal fun JsonMessage.søknadUuid(): UUID = this["søknad_uuid"].asText().let { UUID.fromString(it) }

internal fun JsonNode.asZonedDateTime(): ZonedDateTime = asText().let { ZonedDateTime.parse(it) }

internal fun JsonNode.asUUID(): UUID = this.asText().let { UUID.fromString(it) }
