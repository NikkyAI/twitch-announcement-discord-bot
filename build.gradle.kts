@file:Suppress("GradlePackageUpdate")

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.github.johnrengelman.shadow")
    id("org.jetbrains.qodana")
    application
}

group = "moe.nikky"
version = "1.0-SNAPSHOT"

qodana {
    dockerImageName.set("jetbrains/qodana-jvm:latest")
    saveReport.set(true)
    showReport.set(true)
    showReportPort.set(8089)
}

repositories {
    mavenCentral()
    maven(url = "https://maven.kotlindiscord.com/repository/maven-public/") {
        name = "kotlindiscord"
    }
    maven(url = "https://s01.oss.sonatype.org/content/repositories/snapshots/") {
        name = "klogging-snapshot"
    }
}

application {
    mainClass.set("moe.nikky.MainKt")
}

kotlin {
    this.target {

    }

}

dependencies {
//    implementation("dev.kord:kord-core:_")
    implementation("com.kotlindiscord.kord.extensions:kord-extensions:_")

    implementation("org.jetbrains.kotlinx:kotlinx-datetime:_")

//    implementation(KotlinX.serialization.json)

    implementation(KotlinX.coroutines.core)
    implementation(KotlinX.coroutines.debug)

    implementation("br.com.colman:dice-helper:_")

    implementation(Square.okio)

//    implementation("io.github.microutils:kotlin-logging:_")

    implementation("io.klogging:klogging-jvm:_")
    implementation("io.klogging:slf4j-klogging:_")

    implementation("io.ktor:ktor-client-logging:_")
    implementation("org.slf4j:slf4j-api:_")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
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