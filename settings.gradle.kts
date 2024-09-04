pluginManagement {
    repositories {
//		mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("com.gradle.develocity") version "3.17"
////                        # available:"3.17.1"
////                        # available:"3.17.2"
////                        # available:"3.17.3"
////                        # available:"3.17.4"
////                        # available:"3.17.5"
////                        # available:"3.17.6"
////                        # available:"3.18"
    id("de.fayard.refreshVersions") version "0.60.5"
}

refreshVersions {
    extraArtifactVersionKeyRules(file("version_key_rules.txt"))
}

develocity {
    buildScan {
        tag("discordbot")
        termsOfUseUrl = "https://gradle.com/help/legal-terms-of-use"
        termsOfUseAgree = "yes"
        buildScanPublished {
            file("buildscan.log").appendText("${java.util.Date()} - $buildScanUri\n")
        }
    }
}

rootProject.name = "twitch-announcement-discordbot"