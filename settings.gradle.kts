pluginManagement {
    repositories {
//		mavenLocal()
        gradlePluginPortal()
    }
}

plugins {
    id("com.gradle.enterprise") version "3.7"
////                        # available:"3.7.1"
    id("de.fayard.refreshVersions") version "0.23.0"
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

rootProject.name = "discordbot"