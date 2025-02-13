package no.nav.dagpenger.innsending.html

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.jackson.jackson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import no.nav.dagpenger.innsending.serder.JsonHtmlMapper
import no.nav.dagpenger.innsending.tjenester.PersonaliaOppslag
import java.time.ZonedDateTime
import java.util.UUID

internal class InnsendingSupplier(
    private val dpSoknadBaseUrl: String,
    tokenSupplier: () -> String,
    private val personaliOppslag: PersonaliaOppslag,
) {
    private val httpKlient: HttpClient =
        HttpClient(CIO) {
            expectSuccess = true
            defaultRequest {
                header("Authorization", "Bearer ${tokenSupplier.invoke()}")
            }
            install(ContentNegotiation) {
                jackson { }
            }
            install(Logging) {
                level = LogLevel.INFO
            }
        }

    internal enum class InnsendingType {
        DAGPENGER,
        GENERELL,
    }

    suspend fun hentSoknad(
        id: UUID,
        fnr: String,
        innsendtTidspunkt: ZonedDateTime,
        språk: Innsending.InnsendingsSpråk,
        innsendingType: InnsendingType,
    ): Innsending =
        withContext(Dispatchers.IO) {
            val fakta = async { hentFakta(id) }
            val tekst = async { hentTekst(id) }
            val dokumentasjonsKrav = async { hentDokumentasjonKrav(id) }
            val deferredPerson = async { personaliOppslag.hentPerson(fnr) }
            JsonHtmlMapper(
                innsendingsData = fakta.await(),
                dokumentasjonKrav = dokumentasjonsKrav.await(),
                tekst = tekst.await(),
                språk = språk,
            ).parse(innsendingType).also {
                val person = deferredPerson.await()
                it.infoBlokk =
                    Innsending.InfoBlokk(
                        fødselsnummer = fnr,
                        innsendtTidspunkt = innsendtTidspunkt,
                        navn = person.navn.formatertNavn,
                        adresse = person.adresse.formatertAdresse,
                    )
            }
        }

    suspend fun hentEttersending(
        id: UUID,
        fnr: String,
        innsendtTidspunkt: ZonedDateTime,
        språk: Innsending.InnsendingsSpråk,
        innsendingCopyFunc: Innsending.() -> Innsending = { this },
    ): Innsending =
        withContext(Dispatchers.IO) {
            val tekst = async { hentTekst(id) }
            val dokumentasjonsKrav = async { hentDokumentasjonKrav(id) }
            val deferredPerson = async { personaliOppslag.hentPerson(fnr) }
            JsonHtmlMapper(
                innsendingsData = null,
                dokumentasjonKrav = dokumentasjonsKrav.await(),
                tekst = tekst.await(),
                språk = språk,
            ).parseEttersending()
                .also {
                    val person = deferredPerson.await()
                    it.infoBlokk =
                        Innsending.InfoBlokk(
                            fødselsnummer = fnr,
                            innsendtTidspunkt = innsendtTidspunkt,
                            navn = person.navn.formatertNavn,
                            adresse = person.adresse.formatertAdresse,
                        )
                }.innsendingCopyFunc()
        }

    internal suspend fun hentFakta(id: UUID): String = httpKlient.get("$dpSoknadBaseUrl/$id/ferdigstilt/fakta").bodyAsText()

    internal suspend fun hentTekst(id: UUID): String = httpKlient.get("$dpSoknadBaseUrl/$id/ferdigstilt/tekst").bodyAsText()

    internal suspend fun hentDokumentasjonKrav(id: UUID): String =
        httpKlient.get("$dpSoknadBaseUrl/soknad/$id/dokumentasjonskrav").bodyAsText()
}
