package no.nav.dagpenger.innsending

import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.openapi.Configuration
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.openapi.models.V1Secret
import io.kubernetes.client.util.ClientBuilder
import io.kubernetes.client.util.KubeConfig
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.innsending.html.Innsending.InnsendingsSpråk.BOKMÅL
import no.nav.dagpenger.innsending.html.InnsendingSupplier
import no.nav.dagpenger.innsending.tjenester.Adresse
import no.nav.dagpenger.innsending.tjenester.PDLPersonaliaOppslag
import no.nav.dagpenger.innsending.tjenester.Personalia
import no.nav.dagpenger.innsending.tjenester.PersonaliaOppslag
import no.nav.dagpenger.oauth2.CachedOauth2Client
import no.nav.dagpenger.oauth2.OAuth2Config
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileReader
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.util.UUID

internal class E2ESupplierTest {
    // Hente azuread eller tokenx secret for  app
// jwker.nais.io -> tokenx,  azurerator.nais.io -> azuread
    fun getAuthEnv(
        app: String,
        type: String = "jwker.nais.io",
    ): Map<String, String> {
        // file path to your KubeConfig
        val kubeConfigPath = System.getenv("KUBECONFIG")

        // IF this fails do kubectl get pod to aquire credentials
        val client: ApiClient = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(FileReader(kubeConfigPath))).build()
        Configuration.setDefaultApiClient(client)
        return CoreV1Api()
            .listNamespacedSecret(
                "teamdagpenger",
                null,
                null,
                null,
                null,
                "app=$app,type=$type",
                null,
                null,
                null,
                null,
                null,
                null,
            ).items
            .also { secrets ->
                secrets.sortByDescending<V1Secret?, OffsetDateTime> { it?.metadata?.creationTimestamp }
            }.first<V1Secret?>()
            ?.data!!
            .mapValues { e -> String(e.value) }
    }

    fun getAzureAdToken(
        app: String,
        scope: String,
    ): String {
        val azureadConfig =
            OAuth2Config.AzureAd(
                getAuthEnv(app, "azurerator.nais.io"),
            )
        val tokenAzureAdClient: CachedOauth2Client by lazy {
            CachedOauth2Client(
                tokenEndpointUrl = azureadConfig.tokenEndpointUrl,
                authType = azureadConfig.clientSecret(),
            )
        }

        return tokenAzureAdClient.clientCredentials(scope).access_token ?: throw IllegalStateException("Kunne ikke få tak i token")
    }

    val mockPersonaliaOppslag =
        mockk<PersonaliaOppslag>().also {
            coEvery { it.hentPerson(any()) } returns
                Personalia(
                    navn =
                        Personalia.Navn(
                            forNavn = "fornavn",
                            mellomNavn = "mellomnavn",
                            etterNavn = "etternavn",
                        ),
                    adresse = Adresse.TOM_ADRESSE,
                )
        }

    val personaliaOppslag =
        PDLPersonaliaOppslag(
            pdlUrl = "",
            tokenProvider = {
                getAzureAdToken(
                    "dp-behov-soknad-pdf",
                    scope = "api://dev-fss.pdl.pdl-api/.default",
                )
            },
        )

    @Test
    @Disabled
    fun `hent dokumentasjonskrav`() {
        val ids =
            listOf(
                "ident" to "1a2bed68-68e4-4794-990a-7bfae60b4139",
            )
        val innsendingSupplier =
            InnsendingSupplier(
                dpSoknadBaseUrl = "https://arbeid.dev.nav.no/arbeid/dagpenger/soknadapi",
                tokenSupplier = {
                    getAzureAdToken(
                        "dp-behov-soknad-pdf",
                        "api://dev-gcp.teamdagpenger.dp-soknad/.default",
                    )
                },
                personaliOppslag = personaliaOppslag,
            )

        val innsendingType = InnsendingSupplier.InnsendingType.DAGPENGER
        runBlocking {
            ids.forEach { id ->
                val uuid = UUID.fromString(id.second)
                innsendingSupplier
                    .hentSoknad(
                        id = uuid,
                        fnr = id.first,
                        innsendtTidspunkt = ZonedDateTime.now(),
                        språk = BOKMÅL,
                        innsendingType = innsendingType,
                    ).let { innsending ->
                        lagArkiverbartDokument(innsending).forEach { doc ->
                            File("./build/tmp/søknad-${doc.variant.name}.pdf").writeBytes(doc.pdf)
                        }
                    }

                innsendingSupplier.hentDokumentasjonKrav(uuid).also {
                    File("./build/tmp/dokkrav-$id.json").writeText(it)
                }
                innsendingSupplier.hentFakta(uuid).also {
                    File("./build/tmp/fakta-$id.json").writeText(it)
                }

                innsendingSupplier.hentTekst(uuid).also {
                    File("./build/tmp/tekst-$id.json").writeText(it)
                }
            }
        }
    }
}
