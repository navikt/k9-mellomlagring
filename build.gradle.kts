import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val dusseldorfKtorVersion = "4.0.10"
val ktorVersion = "2.3.5"
val slf4jVersion = "2.0.9"
val amazonawsVersion = "1.11.790"
val tikaVersion = "2.9.1"
val gcpStorageVersion = "2.29.0"
val fuelVersion = "2.3.1"
val mockKVersion = "1.13.8"
val jsonassertVersion = "1.5.1"
val systemRulesVersion = "1.19.0"
val tokenSupportVersion = "3.1.7"
val mockOauth2ServerVersion = "2.0.1"

val mainClass = "no.nav.helse.K9MellomlagringKt"

plugins {
    kotlin("jvm") version "1.9.10"
    id("org.sonarqube") version "4.4.1.3373"
    jacoco
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    // Server
    implementation("no.nav.helse:dusseldorf-ktor-core:$dusseldorfKtorVersion")
    implementation("no.nav.helse:dusseldorf-ktor-jackson:$dusseldorfKtorVersion")
    implementation("no.nav.helse:dusseldorf-ktor-metrics:$dusseldorfKtorVersion")
    implementation("no.nav.helse:dusseldorf-ktor-health:$dusseldorfKtorVersion")
    implementation("no.nav.helse:dusseldorf-ktor-auth:$dusseldorfKtorVersion")
    implementation("com.github.kittinunf.fuel:fuel:$fuelVersion")
    implementation("com.github.kittinunf.fuel:fuel-coroutines:$fuelVersion") {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
    }

    // Token validation
    implementation("no.nav.security:token-validation-ktor-v2:$tokenSupportVersion")

    // Lagring
    implementation("com.google.cloud:google-cloud-storage:$gcpStorageVersion") {
        exclude(group = "com.google.guava", module = "listenablefuture")
    }

    implementation("org.slf4j:jcl-over-slf4j:$slf4jVersion")

    // Sjekke dokumenter
    implementation("org.apache.tika:tika-core:$tikaVersion")

    // Test
    testImplementation("no.nav.security:mock-oauth2-server:$mockOauth2ServerVersion")
    testImplementation("no.nav.helse:dusseldorf-test-support:$dusseldorfKtorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
    }
    testImplementation("io.mockk:mockk:$mockKVersion")
    testImplementation("org.skyscreamer:jsonassert:$jsonassertVersion")
    testImplementation("com.github.stefanbirkner:system-rules:$systemRulesVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
}

repositories {
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/navikt/dusseldorf-ktor")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: "k9-mellomlagring"
            password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }

    maven("https://jitpack.io")
    mavenCentral()
}


java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks {

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    named<KotlinCompile>("compileTestKotlin") {
        kotlinOptions.jvmTarget = "17"
    }

    withType<ShadowJar> {
        archiveBaseName.set("app")
        archiveClassifier.set("")
        manifest {
            attributes(
                mapOf(
                    "Main-Class" to mainClass
                )
            )
        }
    }

    withType<Wrapper> {
        gradleVersion = "8.2.1"
    }

    withType<Test> {
        useJUnitPlatform()
        finalizedBy(jacocoTestReport) // report is always generated after tests run
    }

    jacocoTestReport {
        dependsOn(test) // tests are required to run before generating the report
        reports {
            xml.required.set(true)
            csv.required.set(false)
        }
    }

}

sonarqube {
    properties {
        property("sonar.projectKey", "navikt_k9-mellomlagring")
        property("sonar.organization", "navikt")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.login", System.getenv("SONAR_TOKEN"))
        property("sonar.sourceEncoding", "UTF-8")
    }
}
