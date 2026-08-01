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
        google()
        mavenCentral()
    }
}

rootProject.name = "android-toolkit"

include(":base")
include(":core")
include(":nav")
include(":net")
include(":net:retrofit")
include(":suite")
include(":suite-analytics-umeng")
project(":suite-analytics-umeng").projectDir = file("suite/analytics/umeng")
include(":suite-nav-compose")
project(":suite-nav-compose").projectDir = file("suite/nav/compose")
include(":github:model:core")
include(":github:trending")
