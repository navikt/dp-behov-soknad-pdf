package no.nav.dagpenger.innsending

internal data class BehovSvar(
    val metainfo: MetaInfo,
    val urn: String,
) {
    data class MetaInfo(
        val innhold: String,
        val filtype: String = "PDF",
        val variant: String,
    )
}
