package no.nav.dagpenger.innsending.pdf

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.serialization.jackson.jackson
import no.nav.dagpenger.innsending.ArkiverbartDokument
import no.nav.dagpenger.innsending.LagretDokument
import java.time.ZonedDateTime

class PdfLagring(
    private val baseUrl: String,
    tokenSupplier: () -> String,
    engine: HttpClientEngine = CIO.create(),
) {
    private val httpKlient: HttpClient =
        HttpClient(engine) {
            defaultRequest {
                header("Authorization", "Bearer ${tokenSupplier.invoke()}")
            }
            install(ContentNegotiation) {
                jackson {
                    registerModule(JavaTimeModule())
                    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                }
            }
            install(Logging) {
                level = LogLevel.INFO
            }
        }

    internal suspend fun lagrePdf(
        søknadUUid: String,
        arkiverbartDokument: List<ArkiverbartDokument>,
        fnr: String,
    ): List<LagretDokument> =
        httpKlient
            .submitFormWithBinaryData(
                url = "$baseUrl/$søknadUUid",
                formData =
                    formData {
                        arkiverbartDokument.forEach {
                            append(
                                it.filnavn,
                                it.pdf,
                                Headers.build {
                                    append(HttpHeaders.ContentType, ContentType.Application.Pdf.toString())
                                    append(HttpHeaders.ContentDisposition, "filename=${it.filnavn}")
                                },
                            )
                        }
                    },
            ) {
                this.header("X-Eier", fnr)
            }.body<List<URNResponse>>()
            .map {
                val a2 =
                    arkiverbartDokument.single { a ->
                        a.filnavn == it.filnavn
                    }
                LagretDokument(
                    urn = it.urn,
                    variant = a2.variant,
                    filnavn = it.filnavn,
                )
            }
}

internal data class URNResponse(
    val filnavn: String,
    val urn: String,
    val filsti: String,
    val storrelse: Long,
    val tidspunkt: ZonedDateTime,
)
