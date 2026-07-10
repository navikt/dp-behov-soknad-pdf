package no.nav.dagpenger.innsending.serder

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import java.util.UUID

internal fun JsonMessage.ident() = this["ident"].asText()

internal fun JsonNode.asUUID(): UUID = this.asText().let { UUID.fromString(it) }
