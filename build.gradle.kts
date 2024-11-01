import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    application
    alias(libs.plugins.kotlin)
    alias(libs.plugins.spotless)
}

repositories {
    mavenCentral()
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

application {
    applicationName = "dp-behov-soknad-pdf"
    mainClass.set("no.nav.dagpenger.innsending.AppKt")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(kotlin("stdlib"))

    implementation(libs.rapids.and.rivers)
    implementation(libs.konfig)
    implementation(libs.kotlin.logging)
    implementation("no.nav.dagpenger:oauth2-klient:2024.07.23-10.35.4fc49fbf0d7e")
    implementation("no.nav.dagpenger:pdl-klient:2024.09.20-13.31.40516c678fde")
    implementation(libs.ktor.client.logging.jvm)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.jackson)
    implementation(libs.jackson.datatype.jsr310)
    implementation("com.openhtmltopdf:openhtmltopdf-pdfbox:1.0.10")
    implementation("com.openhtmltopdf:openhtmltopdf-slf4j:1.0.10")
    implementation("com.openhtmltopdf:openhtmltopdf-svg-support:1.0.10")
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.11.0")
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.11.0")
    implementation("org.apache.commons:commons-text:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.9.0")
    implementation("no.nav.pam.geography:pam-geography:2.23")

    testImplementation(kotlin("test"))
    testImplementation(libs.mockk)
    testImplementation("io.kotest:kotest-runner-junit5-jvm:5.9.1")
    testImplementation(libs.ktor.client.mock)
    testImplementation("org.verapdf:validation-model:1.26.1")
    testImplementation("de.redsix:pdfcompare:1.2.2")

    // FOr E2E
    testImplementation("io.kubernetes:client-java:21.0.1-legacy")
}

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    kotlin {
        ktlint()
    }

    kotlinGradle {
        ktlint()
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
