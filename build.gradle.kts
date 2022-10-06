import org.gradle.api.JavaVersion.VERSION_17
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm") version Kotlin.version
    id(Spotless.spotless) version Spotless.version
}

apply {
    plugin(Spotless.spotless)
}

repositories {
    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
    maven("https://jitpack.io")
}

application {
    applicationName = "dp-behov-soknad-pdf"
    mainClass.set("no.nav.dagpenger.innsending.AppKt")
}

java {
    targetCompatibility = VERSION_17
}

tasks.withType<Jar>().configureEach {
    dependsOn("test")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes(mapOf("Main-Class" to application.mainClass.get()))
    }

    from(
        configurations.runtimeClasspath.get().map {
            if (it.isDirectory) it else zipTree(it)
        }
    )
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = VERSION_17.toString()
}

dependencies {
    implementation(kotlin("stdlib"))

    implementation("com.github.navikt:rapids-and-rivers:2022092314391663936769.9d5d33074875")

    implementation("com.natpryce:konfig:1.6.10.0")
    implementation("io.github.microutils:kotlin-logging:2.1.21")
    implementation("com.github.navikt.dp-biblioteker:oauth2-klient:2022.05.30-09.37.623ee13a49dd")
    implementation("io.ktor:ktor-client-${"logging"}:2.0.2")
    implementation("io.ktor:ktor-client-${"cio"}:2.0.2")
    implementation("io.ktor:ktor-client-${"content-negotiation"}:2.0.2")
    implementation("io.ktor:ktor-serialization-jackson:2.0.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.12.3")
    implementation("com.openhtmltopdf:openhtmltopdf-pdfbox:1.0.10")
    implementation("com.openhtmltopdf:openhtmltopdf-slf4j:1.0.10")
    implementation("com.openhtmltopdf:openhtmltopdf-svg-support:1.0.10")
    implementation("org.jsoup:jsoup:1.15.3")
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.8.0")
    implementation("org.apache.commons:commons-text:1.10.0")

    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.1")
    testImplementation("io.kotest:kotest-runner-junit5-jvm:5.5.0")
    testImplementation("io.ktor:ktor-client-${"mock"}:2.0.2")
    testImplementation("org.verapdf:validation-model:1.20.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.2")

    // FOr E2E
    testImplementation("io.kubernetes:client-java:16.0.0")
}

spotless {
    kotlin {
        ktlint("0.43.2")
    }
    kotlinGradle {
        target("*.gradle.kts", "buildSrc/**/*.kt*")
        ktlint("0.43.2")
    }
}

tasks.named("compileKotlin") {
    dependsOn("spotlessCheck")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        showExceptions = true
        showStackTraces = true
        exceptionFormat = TestExceptionFormat.FULL
        events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
    }
}
