package no.nav.dagpenger.innsending.tjenester

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.serialization.jackson.jackson
import mu.KotlinLogging
import no.nav.dagpenger.pdl.adresse.AdresseVisitor
import no.nav.dagpenger.pdl.createPersonOppslag
import kotlin.time.Duration.Companion.seconds

private val sikkerlogg = KotlinLogging.logger("tjenestekall")
private val logg = KotlinLogging.logger {}

internal interface PersonaliaOppslag {
    suspend fun hentPerson(ident: String): Personalia
}

internal class PDLPersonaliaOppslag(
    pdlUrl: String,
    private val tokenProvider: () -> String,
) : PersonaliaOppslag {
    private val httpClient =
        HttpClient(CIO) {
            install(ContentNegotiation) {
                jackson {
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    setSerializationInclusion(JsonInclude.Include.NON_NULL)
                    registerModules(JavaTimeModule())
                }
            }
            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = 5)
                exponentialDelay()
            }

            install(HttpTimeout) {
                this.connectTimeoutMillis = 5.seconds.inWholeMilliseconds
                this.requestTimeoutMillis = 15.seconds.inWholeMilliseconds
                this.socketTimeoutMillis = 5.seconds.inWholeMilliseconds
            }

            install(Logging) {
                level = LogLevel.INFO
            }

            defaultRequest {
                header("TEMA", "DAG")
                header("Authorization", "Bearer ${tokenProvider.invoke()}")
                header(
                    "behandlingsnummer",
                    "B286",
                ) // https://behandlingskatalog.intern.nav.no/process/purpose/DAGPENGER/486f1672-52ed-46fb-8d64-bda906ec1bc9
            }
        }

    private val personOppslag = createPersonOppslag(pdlUrl, httpClient)

    override suspend fun hentPerson(ident: String): Personalia =
        try {
            personOppslag.hentPerson(ident).let {
                Personalia(
                    navn =
                        Personalia.Navn(
                            forNavn = it.fornavn,
                            mellomNavn = it.mellomnavn,
                            etterNavn = it.etternavn,
                        ),
                    adresse = AdresseMapper(AdresseVisitor(it).adresser).folkeregistertAdresse ?: Adresse.TOM_ADRESSE,
                )
            }
        } catch (e: Exception) {
            logg.error(e) { "Feil ved henting av person. Se sikkerlogg ident" }
            sikkerlogg.error { "Feil ved henting av person: $ident" }
            Personalia.TOM_PERSONALIA
        }
}

internal data class Personalia(
    val navn: Navn,
    val adresse: Adresse,
) {
    companion object {
        val TOM_PERSONALIA =
            Personalia(
                navn =
                    Navn(
                        forNavn = "",
                        mellomNavn = null,
                        etterNavn = "",
                    ),
                adresse = Adresse.TOM_ADRESSE,
            )
    }

    data class Navn(
        val forNavn: String,
        val mellomNavn: String?,
        val etterNavn: String,
    ) {
        val formatertNavn: String = listOfNotNull(forNavn, mellomNavn, etterNavn).joinToString(" ")
    }
}
