pluginManagement {
    repositories {
//		mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("com.gradle.enterprise") version "3.15"
    id("de.fayard.refreshVersions") version "0.60.3"
}

refreshVersions {
    extraArtifactVersionKeyRules(file("version_key_rules.txt"))
}

// https://dev.to/jmfayard/the-one-gradle-trick-that-supersedes-all-the-others-5bpg
gradleEnterprise {
    buildScan {
        // uncomment this to scan every gradle task
//		publishAlways()
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
        buildScanPublished {
            file("buildscan.log").appendText("${java.util.Date()} - $buildScanUri\n")
        }
    }
}

rootProject.name = "twitch-announcement-discordbot"