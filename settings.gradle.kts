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
include(":suite-performance-umeng")
project(":suite-performance-umeng").projectDir = file("suite/performance/umeng")
include(":suite-nav-compose")
project(":suite-nav-compose").projectDir = file("suite/nav/compose")
include(":suite-nav-umeng-ulink")
project(":suite-nav-umeng-ulink").projectDir = file("suite/nav/umeng/ulink")
include(":suite-share-umeng")
project(":suite-share-umeng").projectDir = file("suite/share/umeng")
include(":suite-share-umeng-sms")
project(":suite-share-umeng-sms").projectDir = file("suite/share/umeng/sms")
include(":suite-share-umeng-email")
project(":suite-share-umeng-email").projectDir = file("suite/share/umeng/email")
include(":github:model:core")
include(":github:trending")
