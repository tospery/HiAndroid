# HiAndroid

Reusable Kotlin and Android libraries extracted from the AtlasHub project.

HiAndroid provides platform-independent abstractions, Android and
Jetpack Compose utilities, Retrofit integration, and reusable GitHub
models and GitHub Trending parsing APIs. It does not contain AtlasHub
application-specific business logic.

> **Status:** This project is in pre-release development. Public APIs may change before `1.0.0`.

## Modules

| Gradle module | Maven coordinate | Package | Purpose |
| --- | --- | --- | --- |
| `:base` | `com.tospery:base:0.0.2` | JAR | Pure Kotlin base models, logging and analytics abstractions. |
| `:core` | `com.tospery:core:0.0.2` | AAR | Reusable Android platform utilities without Jetpack Compose. |
| `:nav` | `com.tospery:nav:0.0.2` | JAR | Platform-independent URL and URI navigation abstractions. |
| `:net` | `com.tospery:net:0.0.2` | JAR | Platform-independent networking contracts and models. |
| `:net:retrofit` | `com.tospery:net-retrofit:0.0.2` | JAR | Retrofit, OkHttp and Moshi implementation for `:net`. |
| `:suite` | `com.tospery:suite:0.0.2` | AAR | Reusable Android app utilities and Compose components. |
| `:github:model:core` | `com.tospery:github-model-core:0.0.2` | JAR | Stable models shared by GitHub integrations. |
| `:github:trending` | `com.tospery:github-trending:0.0.2` | JAR | GitHub Trending URL building, fetching and HTML parsing. |

### Module layering

- `:base` is pure Kotlin and has no third-party runtime dependencies.
- `:core` holds Android platform utilities. It may depend only on AndroidX
  core/annotation libraries and Kotlin coroutines; it never depends on
  Jetpack Compose or vendor SDKs, so non-Compose apps can use it.
- `:suite` holds Compose components and heavier integrations such as Coil,
  Media3, Timber and Retrofit.

`:core` currently provides string resource access, the Android Keystore
backed `SecureStringStore`, the `AppCacheStore` / `AppCacheManager`
cache abstractions, `Context` helpers (`findActivity`,
`copyPlainTextToClipboard`, `safeStartActivity`,
`appPackageVersionOrNull`), `GooglePlayStoreLauncher` and
`SupportedLinksSettingsLauncher`.

`:suite` also provides `CoilAppCacheStore` (an `AppCacheStore` for a Coil
`ImageLoader`), `SuiteHorizontalDivider` (a one physical pixel divider) and
`SuitePdfViewer` (pinch-to-zoom PDF pages rendered with `PdfRenderer`, with
its own localized error text). `:net` provides `SseLineParser` for
extracting `data:` payloads from line-based Server-Sent Events streams.

### Shared Android UI strings

`:suite` publishes `suite_action_retry` (`Retry` / `重试`) and
`suite_navigate_back_content_description` (`Navigate back` / `返回`) through
`com.tospery.suite.R.string`. They are listed in
`suite/src/main/res/values/public.xml`; other component strings stay internal.
The web page's Back action retains its own description because it can navigate
within browser history. Keep default and Chinese values together when changing
these resources.

## Build requirements

- JDK 21 for running the configured Gradle daemon.
- Gradle 9.5.0 through the included Gradle Wrapper.
- Android SDK 37 for building `:core` and `:suite`.
- Android `minSdk` 30 for `:core` and `:suite`.
- Java and Kotlin JVM bytecode target 11.

## Build from source

Clone the repository and run the complete verification suite:

```bash
git clone https://github.com/tospery/HiAndroid.git
cd HiAndroid
./gradlew clean check
```

Publish all modules to Maven Local for local dependency testing:

```bash
./gradlew publishToMavenLocal
```

Published local artifacts are written below:

```text
~/.m2/repository/com/tospery/
```

## Consume from Maven Central

After a version has been published to Maven Central, add the repository
and only the modules required by the application:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

Example dependencies:

```kotlin
dependencies {
    implementation("com.tospery:suite:0.0.2")
    implementation("com.tospery:github-trending:0.0.2")
}
```

`:suite` exposes the public abstractions from `:base`, `:core`, `:nav`
and `:net`. Add `com.tospery:net-retrofit` explicitly when application
code needs to use its Retrofit or OkHttp integration APIs directly.

## Local source integration

### Compose action menus

`:suite` provides `SuiteActionMenu` and `SuiteActionMenuItem` for anchored,
text-only action menus. Place the menu beside its trigger inside the same `Box`;
the parent bounds determine the anchor. The component uses a shared charcoal
background, white text, 16dp corners and at least 48dp touch targets in both
light and dark themes. It constrains the popup to the screen, points its arrow
at the trigger center, and moves above the trigger when there is more room.
Width follows the longest label plus 30dp padding on each side (60dp total),
bounded only by the available window width. Long content scrolls. Callers own
labels, ordering and callbacks; selecting an enabled item dismisses the menu before
invoking its callback.

### Composite Build

A consuming Gradle project can keep Maven coordinates in its dependency
declarations while replacing them with a local source checkout through
a Composite Build.

Add the following to the consuming project's `settings.gradle.kts` and
adjust the local path as needed:

```kotlin
includeBuild("../HiAndroid") {
    dependencySubstitution {
        substitute(module("com.tospery:base"))
            .using(project(":base"))
        substitute(module("com.tospery:core"))
            .using(project(":core"))
        substitute(module("com.tospery:nav"))
            .using(project(":nav"))
        substitute(module("com.tospery:net"))
            .using(project(":net"))
        substitute(module("com.tospery:net-retrofit"))
            .using(project(":net:retrofit"))
        substitute(module("com.tospery:suite"))
            .using(project(":suite"))
        substitute(module("com.tospery:github-model-core"))
            .using(project(":github:model:core"))
        substitute(module("com.tospery:github-trending"))
            .using(project(":github:trending"))
    }
}
```

The consuming module continues to use normal Maven coordinates:

```kotlin
dependencies {
    implementation("com.tospery:suite:0.0.2")
}
```

Gradle then compiles the local HiAndroid source instead of downloading the
matching Maven artifact. Removing or disabling `includeBuild` switches
the consumer back to the published artifact without changing dependency
declarations.

## Versioning

The project follows semantic versioning:

- `0.0.x` versions are pre-release development versions.
- Public APIs may change between `0.0.x` versions.
- `1.0.0` will be released when the public APIs are stable and the host
  application is ready for its public release.
- Published release versions are immutable and must never be overwritten.

## License

HiAndroid is licensed under the
[Apache License 2.0](LICENSE).

## Optional U-Share WeChat platform

`:suite-share-umeng-wechat` is consumed through the parent composite build as
`com.tospery:suite-share-umeng-wechat`; it is not currently published to Maven Central.
It adds U-Share 7.3.7's WeChat handler and WeChat OpenSDK 6.8.24 without forcing
those dependencies on SMS/email-only hosts. The host provides its public AppID,
a narrow FileProvider authority and a callback activity at
`<applicationId>.wxapi.WXEntryActivity` extending `UmengWeChatCallbackActivity`.
The callback readiness check must reflect the host's consent-aware runtime.
Sharing uses an empty AppSecret; authorization and secret exchange belong on a server.

`ShareContent.image` accepts portable HTTPS or local-file sources. Image selection,
resource loading and app-specific thumbnail policy stay in the host application.
