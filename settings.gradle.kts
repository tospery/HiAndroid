pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        maven("https://maven.aliyun.com/nexus/content/repositories/releases/")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://maven.aliyun.com/nexus/content/repositories/releases/")
        mavenCentral()
    }
}

rootProject.name = "HiAndroid"

include(":base")
include(":core")
include(":nav")
include(":nav:compose")
include(":nav:umeng-ulink")
project(":nav:umeng-ulink").projectDir = file("nav/umeng/ulink")
include(":net")
include(":net:retrofit")
include(":store")
include(":store:datastore")
include(":store:keystore")
include(":suite")
include(":suite:coil")
include(":suite:media3")
include(":suite:log-timber")
include(":suite:aliyun-emas-core")
project(":suite:aliyun-emas-core").projectDir = file("suite/aliyun/emas/core")
include(":suite:umeng-core")
project(":suite:umeng-core").projectDir = file("suite/umeng/core")
include(":suite:analytics-umeng")
project(":suite:analytics-umeng").projectDir = file("suite/analytics/umeng")
include(":suite:performance-umeng")
project(":suite:performance-umeng").projectDir = file("suite/performance/umeng")
include(":suite:socialsharing-umeng")
project(":suite:socialsharing-umeng").projectDir = file("suite/socialsharing/umeng")
include(":suite:socialsharing-umeng-sms")
project(":suite:socialsharing-umeng-sms").projectDir = file("suite/socialsharing/umeng/sms")
include(":suite:socialsharing-umeng-wechat")
project(":suite:socialsharing-umeng-wechat").projectDir = file("suite/socialsharing/umeng/wechat")
include(":suite:socialsharing-umeng-email")
project(":suite:socialsharing-umeng-email").projectDir = file("suite/socialsharing/umeng/email")
include(":hiandroid")
include(":github:model:core")
include(":github:trending")
