@file:Suppress("GradlePackageUpdate")

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.github.johnrengelman.shadow")
    application
}

group = "moe.nikky"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven(url = "https://maven.kotlindiscord.com/repository/maven-public/") {
        name = "kotlindiscord"
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

    implementation("io.ktor:ktor-client-logging:_")
    implementation("io.github.microutils:kotlin-logging:_")
    implementation("ch.qos.logback:logback-classic:_")


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
                commandLine("docker", "build", "-t", "nikkyai/discordbot:latest", "-f", "local.Dockerfile", ".")
            }
        }
    }
    register("pushImage") {
        group = "build"
        dependsOn("buildImage")
        doLast {
            exec {
                commandLine("docker", "push", "nikkyai/discordbot:latest")
            }
        }
    }
}