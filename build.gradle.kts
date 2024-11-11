@file:Suppress("GradlePackageUpdate")

import dev.kordex.gradle.plugins.kordex.DataCollection

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.github.johnrengelman.shadow")
    id("dev.kordex.gradle.kordex")
//    id("dev.kordex.gradle.docker") version "1.4.2"
    id("dev.reformator.stacktracedecoroutinator")
}

group = "moe.nikky"
version = "1.0-SNAPSHOT"

//docker {
//    generateOnBuild = true
//    target = file("test_DockerFile")
//    commands {
////        add(DockerfileCommand)
//    }
//}
kordEx {
    bot {
        mainClass = "moe.nikky.MainKt"
        processDotEnv = true
        voice = false
        dataCollection = DataCollection.None
    }
    addDependencies = true
    addRepositories = true
//    kordVersion.set("latest")
//    kordExVersion.set("latest")
//    module("extra-phishing")
//    module("extra-pluralkit")
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
//    implementation("com.kotlindiscord.kord.extensions:kord-extensions:_")
//    implementation("com.kotlindiscord.kord.extensions:annotations:_")
//    implementation("com.kotlindiscord.kord.extensions:extra-phishing:_")
//    implementation("com.kotlindiscord.kord.extensions:extra-pluralkit:_")
//    ksp("com.kotlindiscord.kord.extensions:annotation-processor:_")

//    implementation("dev.kord:kord-core:_")

    implementation("dev.kord.x:emoji:_")

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
stacktraceDecoroutinator {
    enabled = true
}