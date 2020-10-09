import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

val dusseldorfKtorVersion = "1.3.2.315ec09"
val ktorVersion = ext.get("ktorVersion").toString()
val slf4jVersion = ext.get("slf4jVersion").toString()
val amazonawsVersion = "1.11.790"
val tikaVersion = "1.24.1"
val gcpStorageVersion = "1.113.1"

val mainClass = "no.nav.helse.K9MellomlagringKt"

plugins {
    kotlin("jvm") version "1.3.72"
    id("com.github.johnrengelman.shadow") version "5.2.0"
}

buildscript {
    apply("https://raw.githubusercontent.com/navikt/dusseldorf-ktor/315ec09dad3639f43b6d4b6c267e01037254873e/gradle/dusseldorf-ktor.gradle.kts")
}

dependencies {
    // Server
    implementation ( "no.nav.helse:dusseldorf-ktor-core:$dusseldorfKtorVersion")
    implementation ( "no.nav.helse:dusseldorf-ktor-jackson:$dusseldorfKtorVersion")
    implementation ( "no.nav.helse:dusseldorf-ktor-metrics:$dusseldorfKtorVersion")
    implementation ( "no.nav.helse:dusseldorf-ktor-health:$dusseldorfKtorVersion")
    implementation ( "no.nav.helse:dusseldorf-ktor-auth:$dusseldorfKtorVersion")

    // Client
    implementation ( "no.nav.helse:dusseldorf-ktor-client:$dusseldorfKtorVersion")

    // Lagring
    implementation("com.google.cloud:google-cloud-storage:$gcpStorageVersion")
    implementation("org.slf4j:jcl-over-slf4j:$slf4jVersion")

    // Sjekke dokumenter
    implementation("org.apache.tika:tika-core:$tikaVersion")

    // Test
    testImplementation ( "no.nav.helse:dusseldorf-test-support:$dusseldorfKtorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
    }
    testImplementation("io.mockk:mockk:1.10.0")
    testImplementation("org.skyscreamer:jsonassert:1.5.0")
    testImplementation( "com.github.stefanbirkner:system-rules:1.19.0")

}

repositories {
    maven("https://dl.bintray.com/kotlin/ktor")
    maven("https://kotlin.bintray.com/kotlinx")
    maven("http://packages.confluent.io/maven/")

    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/navikt/dusseldorf-ktor")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_USERNAME")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }

    jcenter()
    mavenLocal()
    mavenCentral()
}


java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}


tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.named<KotlinCompile>("compileTestKotlin") {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.withType<ShadowJar> {
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

tasks.withType<Wrapper> {
    gradleVersion = "6.4.1"
}
