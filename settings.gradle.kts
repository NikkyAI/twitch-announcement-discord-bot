pluginManagement {
    repositories {
//		mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("com.gradle.enterprise") version "3.7.1"
////                        # available:"3.7.2"
////                        # available:"3.8"
////                        # available:"3.8.1"
    id("de.fayard.refreshVersions") version "0.23.0"
////                            # available:"0.30.0"
////                            # available:"0.30.1"
////                            # available:"0.30.2"
////                            # available:"0.40.0"
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