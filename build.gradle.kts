@file:Suppress("GradlePackageUpdate")

import de.fayard.refreshVersions.core.versionFor


plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.github.johnrengelman.shadow")
    id("app.cash.sqldelight")
    application
}

group = "moe.nikky"
version = "1.0-SNAPSHOT"

repositories {
    google()
    mavenCentral()

    maven(url = "https://oss.sonatype.org/content/repositories/snapshots") {
        name = "Sonatype Snapshots (Legacy)"
    }
    maven(url = "https://s01.oss.sonatype.org/content/repositories/snapshots/") {
        name = "Sonatype Snapshots"
    }

//    maven(url = "https://maven.kotlindiscord.com/repository/maven-public/") {
//        name = "Kotlin Discord"
//    }
}

application {
    mainClass.set("moe.nikky.MainKt")
}

kotlin {
    sourceSets.main {
        kotlin.srcDir("build/generated/ksp/main/kotlin")
    }
    sourceSets.test {
        kotlin.srcDir("build/generated/ksp/test/kotlin")
    }
}

dependencies {
//    implementation("dev.kord:kord-core:_")
    implementation("com.kotlindiscord.kord.extensions:kord-extensions:_")
    implementation("com.kotlindiscord.kord.extensions:annotations:_")
    implementation("com.kotlindiscord.kord.extensions:extra-phishing:_")
    implementation("com.kotlindiscord.kord.extensions:extra-pluralkit:_")
    ksp("com.kotlindiscord.kord.extensions:annotation-processor:_")

//    implementation("dev.kord:kord-core:_")

    implementation("dev.kord.x:emoji:_")

//    implementation("dev.kord.cache:cache-map:_")

    implementation("org.jetbrains.kotlinx:kotlinx-datetime:_")

    implementation(KotlinX.serialization.json)
    implementation("io.ktor:ktor-serialization-kotlinx-json:_")

    implementation(KotlinX.coroutines.core)
    implementation(KotlinX.coroutines.debug)
//    implementation(KotlinX.serialization.json)

    implementation("br.com.colman:dice-helper:_")

    implementation("app.cash.sqldelight:sqlite-driver:_")
    implementation("app.cash.sqldelight:primitive-adapters:_")
    implementation("app.cash.sqldelight:coroutines-extensions:_")

    implementation(Square.okio)

    implementation("io.klogging:klogging-jvm:_")
    implementation("io.klogging:slf4j-klogging:_")
//    implementation("io.github.oshai:kotlin-logging")

    implementation("io.ktor:ktor-client-logging:_")
    implementation("org.slf4j:slf4j-api:_")

    testImplementation(Testing.Junit.jupiter.api)
    testRuntimeOnly(Testing.Junit.jupiter.engine)
}

java {
    sourceCompatibility = JavaVersion.VERSION_16
    targetCompatibility = JavaVersion.VERSION_16
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "16"
    }
}

sqldelight {
    databases {
        create("DiscordbotDatabase") {
            packageName.set("moe.nikky.db")
//            generateAsync.set(true)
            deriveSchemaFromMigrations.set(true)
            schemaOutputDirectory.set(file("src/main/sqldelight/databases"))
            dialect("app.cash.sqldelight:sqlite-3-38-dialect:${versionFor("version.app.cash.sqldelight")}")
            verifyMigrations.set(true)
        }
    }
//    Database { // This will be the name of the generated database class.
//        packageName = "com.example"
//    }
}

tasks {
    test {
        useJUnitPlatform()
    }
    shadowJar {
        archiveBaseName.set("application")
        archiveClassifier.set("")
        archiveVersion.set("")
    }

    register("buildImage") {
        group = "build"
        dependsOn(shadowJar)
        doLast {
            exec {
                commandLine("docker", "build", "-t", "nikkyai/discordbot:dev", "-f", "local.Dockerfile", ".")
            }
        }
    }
//    register("pushImage") {
//        group = "build"
//        dependsOn("buildImage")
//        doLast {
//            exec {
//                commandLine("docker", "push", "nikkyai/discordbot:dev")
//            }
//        }
//    }
}