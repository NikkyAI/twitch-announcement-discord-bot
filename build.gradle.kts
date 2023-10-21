@file:Suppress("GradlePackageUpdate")

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.github.johnrengelman.shadow")
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
    implementation("io.github.xn32:json5k:_")
    implementation("io.ktor:ktor-serialization-kotlinx-json:_")

    implementation(KotlinX.coroutines.core)
    implementation(KotlinX.coroutines.debug)

    implementation("br.com.colman:dice-helper:_")

    implementation("io.klogging:klogging-jvm:_")
    implementation("io.klogging:slf4j-klogging:_")

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