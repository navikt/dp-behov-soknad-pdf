package no.nav.dagpenger.innsending.tjenester

import no.nav.dagpenger.innsending.tjenester.PDLAdresseMapper.LandDataDAO.finnLand
import no.nav.dagpenger.innsending.tjenester.PDLAdresseMapper.PostDataDao.finnPoststed
import no.nav.dagpenger.innsending.tjenester.PDLAdresseMapper.formatertAdresse
import no.nav.dagpenger.pdl.adresse.AdresseMapper
import no.nav.dagpenger.pdl.adresse.AdresseMetadata
import no.nav.dagpenger.pdl.adresse.PDLAdresse
import no.nav.dagpenger.pdl.adresse.PostAdresseOrder
import no.nav.pam.geography.Country
import no.nav.pam.geography.CountryDAO
import no.nav.pam.geography.PostDataDAO
import java.io.IOException

data class Adresse(
    val adresselinje1: String = "",
    val adresselinje2: String = "",
    val adresselinje3: String = "",
    val postnummer: String = "",
    val poststed: String? = "",
    val landkode: String = "",
    val land: String = "",
) {
    companion object {
        val TOM_ADRESSE = Adresse()
    }

    val formatertAdresse: String
        get() {
            val l1: String =
                listOfNotNull(this.adresselinje1, this.adresselinje2, this.adresselinje3)
                    .filterNot(String::isBlank)
                    .joinToString(separator = " ")
            val l2 =
                listOfNotNull(this.postnummer, this.poststed)
                    .filterNot(String::isBlank)
                    .joinToString(separator = " ")
            return listOfNotNull(l1, l2, land)
                .filterNot(String::isBlank)
                .joinToString(separator = ", ")
        }
}

internal class AdresseMapper(
    pdlAdresser: List<PDLAdresse>,
) {
    val folkeregistertAdresse: Adresse?
    val postAdresse: Adresse?

    init {
        val sortert = pdlAdresser.sortedWith(PostAdresseOrder.comparator)
        folkeregistertAdresse =
            sortert
                .firstOrNull { it.adresseMetadata.adresseType == AdresseMetadata.AdresseType.BOSTEDSADRESSE }
                ?.let(::formatertAdresse)

        postAdresse = sortert.firstOrNull()?.let(::formatertAdresse)
    }
}

internal object PDLAdresseMapper : AdresseMapper<Adresse>() {
    private class GeografiOppslagInitException(
        e: Exception,
    ) : RuntimeException(e)

    private object PostDataDao {
        private val dao: PostDataDAO =
            try {
                PostDataDAO()
            } catch (e: IOException) {
                throw GeografiOppslagInitException(e)
            }

        fun finnPoststed(postNummer: String?): String? =
            postNummer?.let {
                dao.findPostData(postNummer).map { it.capitalizedCityName }.orElse(null)
            }
    }

    private object LandDataDAO {
        private val dao: CountryDAO =
            try {
                CountryDAO()
            } catch (e: IOException) {
                throw GeografiOppslagInitException(e)
            }

        fun finnLand(landKode: String?): Country? = landKode?.let { dao.findCountryByCode(it).orElse(null) }
    }

    override fun formatertAdresse(pdlAdresse: PDLAdresse.MatrikkelAdresse): Adresse = Adresse()

    override fun formatertAdresse(pdlAdresse: PDLAdresse.PostAdresseIFrittFormat): Adresse {
        with(pdlAdresse) {
            val adresseLinjer =
                listOf(adresseLinje1, adresseLinje2, adresseLinje3)
                    .filterNot(String?::isNullOrBlank)

            return Adresse(
                adresselinje1 = adresseLinjer.getOrNull(0) ?: "",
                adresselinje2 = adresseLinjer.getOrNull(1) ?: "",
                adresselinje3 = adresseLinjer.getOrNull(2) ?: "",
                postnummer = postnummer ?: "",
                poststed = finnPoststed(postnummer) ?: "",
                landkode = "NO",
                land = "NORGE",
            )
        }
    }

    override fun formatertAdresse(pdlAdresse: PDLAdresse.PostboksAdresse): Adresse {
        with(pdlAdresse) {
            val adresseLinjer =
                listOf(postbokseier, postboks)
                    .filterNot(String?::isNullOrBlank)
            return Adresse(
                adresselinje1 = adresseLinjer.getOrNull(0) ?: "",
                adresselinje2 = adresseLinjer.getOrNull(1) ?: "",
                adresselinje3 = "",
                postnummer = postnummer ?: "",
                poststed = finnPoststed(postnummer) ?: "",
                landkode = "NO",
                land = "NORGE",
            )
        }
    }

    override fun formatertAdresse(pdlAdresse: PDLAdresse.TomAdresse): Adresse = Adresse()

    override fun formatertAdresse(pdlAdresse: PDLAdresse.UtenlandsAdresseIFrittFormat): Adresse {
        with(pdlAdresse) {
            val adresseLinjer =
                listOf(adresseLinje1, adresseLinje2, adresseLinje3)
                    .filterNot(String?::isNullOrBlank)

            val land = finnLand(landKode)
            return Adresse(
                adresselinje1 = adresseLinjer.getOrNull(0) ?: "",
                adresselinje2 = adresseLinjer.getOrNull(1) ?: "",
                adresselinje3 = adresseLinjer.getOrNull(2) ?: "",
                postnummer = postkode ?: "",
                poststed = byEllerStedsnavn ?: "",
                landkode = land?.alpha2Code ?: "",
                land = land?.name ?: "",
            )
        }
    }

    override fun formatertAdresse(pdlAdresse: PDLAdresse.UtenlandskAdresse): Adresse {
        with(pdlAdresse) {
            val adresseLinjer =
                listOf(adressenavnNummer, bygningEtasjeLeilighet, postboksNummerNavn)
                    .filterNot(String?::isNullOrBlank)

            val land = finnLand(landKode)
            return Adresse(
                adresselinje1 = adresseLinjer.getOrNull(0) ?: "",
                adresselinje2 = adresseLinjer.getOrNull(1) ?: "",
                adresselinje3 = adresseLinjer.getOrNull(2) ?: "",
                postnummer = postkode ?: "",
                poststed = bySted ?: "",
                landkode = land?.alpha2Code ?: "",
                land = land?.name ?: "",
            )
        }
    }

    override fun formatertAdresse(pdlAdresse: PDLAdresse.VegAdresse): Adresse {
        val husNummerBokstav =
            listOf(pdlAdresse.husnummer, pdlAdresse.husbokstav)
                .filterNot(String?::isNullOrBlank)
                .joinToString("")

        val l1 =
            listOf(pdlAdresse.adressenavn, husNummerBokstav)
                .filterNot(String?::isNullOrBlank)
                .joinToString(separator = " ")

        val adresseLinjer =
            listOf(pdlAdresse.adresseMetadata.coAdresseNavn, l1)
                .filterNot(String?::isNullOrBlank)

        return Adresse(
            adresselinje1 = adresseLinjer.getOrNull(0) ?: "",
            adresselinje2 = adresseLinjer.getOrNull(1) ?: "",
            postnummer = pdlAdresse.postnummer ?: "",
            poststed = finnPoststed(pdlAdresse.postnummer) ?: "",
            // vegadresse er alltid en norsk adresse
            landkode = "NO",
            land = "NORGE",
        )
    }
}
