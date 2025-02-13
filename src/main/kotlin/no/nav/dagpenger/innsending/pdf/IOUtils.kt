package no.nav.dagpenger.innsending.pdf

import java.io.FileNotFoundException
import java.io.InputStream

internal fun String.fileAsInputStream(): InputStream =
    object {}.javaClass.getResource(this)?.openStream()
        ?: throw FileNotFoundException("Could not find file: $this")

internal fun String.fileAsByteArray(): ByteArray = this.fileAsInputStream().use { it.readAllBytes() }

internal fun String.fileAsString(): String =
    this
        .fileAsInputStream()
        .buffered()
        .reader()
        .use { it.readText() }
