package no.nav.dagpenger.innsending.serder

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import tools.jackson.databind.JsonNode
import java.util.UUID

internal fun JsonMessage.ident() = this["ident"].asText()

internal fun JsonNode.asUUID(): UUID = this.asText().let { UUID.fromString(it) }
