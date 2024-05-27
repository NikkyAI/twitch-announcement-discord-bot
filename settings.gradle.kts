pluginManagement {
    repositories {
//		mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("com.gradle.develocity") version "3.17.4"
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
// https://dev.to/jmfayard/the-one-gradle-trick-that-supersedes-all-the-others-5bpg
//gradleEnterprise {
//    buildScan {
//        // uncomment this to scan every gradle task
////		publishAlways()
//        termsOfServiceUrl = "https://gradle.com/terms-of-service"
//        termsOfServiceAgree = "yes"
//        buildScanPublished {
//            file("buildscan.log").appendText("${java.util.Date()} - $buildScanUri\n")
//        }
//    }
//}

rootProject.name = "twitch-announcement-discordbot"