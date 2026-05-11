pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            url = uri("https://maven.google.com")
        }
        maven {
            url = uri("https://dl.google.com/dl/android/maven2")
        }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots")
        }
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/releases")
        }
        maven {
            url = uri("https://repo1.maven.org/maven2")
        }
        maven {
            url = uri("https://pkgs.dev.azure.com/MicrosoftDeviceSDK/DuoSDK-Public/_packaging/Duo-SDK-Feed/maven/v1")
        }
        maven {
            url = uri("https://jcenter.bintray.com")
        }
    }
}

rootProject.name = "ClassNote"
include(":app")
include(":feature-api")
include(":feature-google")
include(":feature-microsoft")
include(":feature-ai")
include(":feature-weather")
