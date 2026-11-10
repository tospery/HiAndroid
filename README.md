# HiAndroid

Reusable Kotlin and Android libraries extracted from the AtlasHub project.

HiAndroid provides platform-independent abstractions, Android and
Jetpack Compose utilities, Retrofit integration, and reusable GitHub
models and GitHub Trending parsing APIs. It does not contain AtlasHub
application-specific business logic.

> **Status:** `1.0.0` remains the published stable release. The source tree
> prepares `2.0.0`; that version is not on Maven Central yet.

## Modules

The following are `2.0.0` source and Maven Local coordinates. Only modules in
the publication whitelist are listed; SMS, email and WeChat social sharing
platform adapters remain source-only until their external SDK requirements are
verified for public publication.

| Gradle module | Maven coordinate | Package | Purpose |
| --- | --- | --- | --- |
| `:base` | `com.tospery:base:2.0.0` | JAR | Pure Kotlin models and logging, analytics, social sharing contracts. |
| `:core` | `com.tospery:core:2.0.0` | AAR | Lightweight Android platform utilities. |
| `:nav` | `com.tospery:nav:2.0.0` | JAR | URL and URI navigation contracts. |
| `:nav:compose` | `com.tospery:nav-compose:2.0.0` | AAR | Jetpack Navigation Compose adapter. |
| `:nav:umeng-ulink` | `com.tospery:nav-umeng-ulink:2.0.0` | AAR | Optional Umeng U-Link adapter. |
| `:net` | `com.tospery:net:2.0.0` | JAR | Network contracts and models. |
| `:net:retrofit` | `com.tospery:net-retrofit:2.0.0` | JAR | Retrofit, OkHttp and Moshi adapter. |
| `:store` | `com.tospery:store:2.0.0` | JAR | Persistence contracts and reusable cache composition. |
| `:store:datastore` | `com.tospery:store-datastore:2.0.0` | AAR | DataStore preferences adapter. |
| `:store:keystore` | `com.tospery:store-keystore:2.0.0` | AAR | Android Keystore secure string adapter. |
| `:suite` | `com.tospery:suite:2.0.0` | AAR | Reusable Compose views. |
| `:suite:coil` | `com.tospery:suite-coil:2.0.0` | AAR | Optional image viewer and Coil cache adapter. |
| `:suite:media3` | `com.tospery:suite-media3:2.0.0` | AAR | Optional Media3 player view. |
| `:suite:log-timber` | `com.tospery:suite-log-timber:2.0.0` | AAR | Optional Timber logger adapter: `TimberAppLogger` forwards complete `LogEntry` values; `LogEntryTree` receives them losslessly (attributes, tag, throwable) and synthesizes entries for direct Timber calls. Whether sensitive data is accepted is declared by the host via `supportsSensitiveData`. |
| `:suite:aliyun-emas-core` | `com.tospery:suite-aliyun-emas-core:2.0.0` | AAR | Optional Alibaba Cloud EMAS adapter. |
| `:suite:umeng-core` | `com.tospery:suite-umeng-core:2.0.0` | AAR | Optional Umeng runtime. |
| `:suite:analytics-umeng` | `com.tospery:suite-analytics-umeng:2.0.0` | AAR | Optional Umeng analytics adapter. |
| `:suite:performance-umeng` | `com.tospery:suite-performance-umeng:2.0.0` | AAR | Optional Umeng U-APM adapter. |
| `:suite:socialsharing-umeng` | `com.tospery:suite-socialsharing-umeng:2.0.0` | AAR | Optional Umeng social sharing adapter. |
| `:hiandroid` | `com.tospery:hiandroid:2.0.0` | AAR | Optional entry for the common modules and default adapters. |
| `:github:model:core` | `com.tospery:github-model-core:2.0.0` | JAR | Shared GitHub models, independent of the base library. |
| `:github:trending` | `com.tospery:github-trending:2.0.0` | JAR | GitHub Trending library, independent of the base library. |

### Module layering

- `:base` has no Android or UI dependencies.
- `:core` contains lightweight Android platform utilities without Compose.
- `:nav`, `:net` and `:store` contain the contracts and common behavior of
  navigation, networking and persistence; their concrete adapters live in
  child modules and depend on the corresponding parent.
- `:store` covers cache, preferences and secure values; application-specific
  Room schema, preference keys and user partitions remain with the consumer.
- `:suite` contains common Compose views. Coil, Media3, Timber and vendor SDKs
  live in opt-in submodules. `:hiandroid` groups common modules and default
  adapters without vendor SDKs or automatic SDK initialization.

`:core` provides string resource access, `Context` helpers, Play Store and
supported-links settings launchers. `:store` provides `SecureStringStore`,
`StringPreferenceStore`, `AppCacheStore` and `AppCacheManager` contracts.
`:store:keystore` supplies Android Keystore encryption; `:store:datastore`
supplies DataStore preferences. `:suite:coil` supplies `CoilAppCacheStore` and
`SuiteImageViewer`, while `:suite:media3` supplies `SuiteMediaPlayerPage`.
`SuiteHorizontalDivider`, `SuitePdfViewer`, `SuiteSnackbarHost` and other
framework-neutral Compose views stay in `:suite`. `:net` supplies the
`SseLineParser` for line-based Server-Sent Events streams.

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
- Kotlin 2.4.10 or newer 2.4-compatible compiler in consuming projects;
  published Kotlin metadata uses version 2.4.
- Android SDK 37 for building Android library modules.
- Android `minSdk` 30 for all Android library modules.
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

## Consume published `1.0.0`

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
    implementation("com.tospery:suite:1.0.0")
    implementation("com.tospery:github-trending:1.0.0")
}
```

In `1.0.0`, `:suite` exposes the public abstractions from `:base`, `:core`,
`:nav` and `:net`. The `2.0.0` source candidate separates these dependencies.

For local `2.0.0` testing, publish to Maven Local and use the exact coordinates
from the module table. A small application may choose the optional entry:

```kotlin
dependencies {
    implementation("com.tospery:hiandroid:2.0.0")
    implementation("com.tospery:github-trending:2.0.0")
}
```

Multi-module applications should declare the smallest relevant artifacts in
each consuming module. Vendor SDKs and optional Coil, Media3 and Timber
adapters are never included by `hiandroid`.

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

When using a sibling source checkout, the consumer must commit a full-SHA
version lock and enforce the Git alignment checks in
[`docs/engineering/sibling-source-dependency.md`](docs/engineering/sibling-source-dependency.md).
The `includeBuild` example below only shows coordinate substitution; it is
not a complete Verification or Release gate.

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
        substitute(module("com.tospery:nav-compose"))
            .using(project(":nav:compose"))
        substitute(module("com.tospery:net"))
            .using(project(":net"))
        substitute(module("com.tospery:net-retrofit"))
            .using(project(":net:retrofit"))
        substitute(module("com.tospery:store"))
            .using(project(":store"))
        substitute(module("com.tospery:store-datastore"))
            .using(project(":store:datastore"))
        substitute(module("com.tospery:store-keystore"))
            .using(project(":store:keystore"))
        substitute(module("com.tospery:suite"))
            .using(project(":suite"))
        substitute(module("com.tospery:suite-coil"))
            .using(project(":suite:coil"))
        substitute(module("com.tospery:suite-media3"))
            .using(project(":suite:media3"))
        substitute(module("com.tospery:suite-log-timber"))
            .using(project(":suite:log-timber"))
        substitute(module("com.tospery:hiandroid"))
            .using(project(":hiandroid"))
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
    implementation("com.tospery:suite:2.0.0")
}
```

Gradle then compiles the local HiAndroid source instead of downloading the
matching Maven artifact. Once `2.0.0` is actually published, removing or
disabling `includeBuild` will switch to that artifact without changing
dependency declarations; until then, use Maven Local for binary tests.

## Versioning

The project follows semantic versioning:

- `0.0.x` versions are pre-release development versions.
- Public APIs may change between `0.0.x` versions.
- `1.0.0` is the first stable release, prepared alongside AtlasHub Android.
- `2.0.0` is the unreleased source candidate for the new module layout and
  social sharing naming.
- Published release versions are immutable and must never be overwritten.

### Migrate from `1.0.0` to `2.0.0`

| `1.0.0` | `2.0.0` |
| --- | --- |
| `com.tospery.core.cache.*` | `com.tospery.store.cache.*` in `store` |
| `com.tospery.core.security.SecureStringStore` | `com.tospery.store.security.SecureStringStore` in `store` |
| `com.tospery.core.security.AndroidKeystoreSecureStringStore` | `com.tospery.store.security.AndroidKeystoreSecureStringStore` in `store-keystore` |
| `com.tospery.base.share.Share*` | `com.tospery.base.socialsharing.SocialSharing*` |
| `suite-nav-compose` | `nav-compose` and package `com.tospery.nav.compose` |
| `suite-nav-umeng-ulink` | `nav-umeng-ulink` and package `com.tospery.nav.umeng.ulink` |
| `suite-share-umeng` | `suite-socialsharing-umeng` and package `com.tospery.suite.socialsharing.umeng` |
| `SuiteImageViewer`, `CoilAppCacheStore` in `suite` | same Kotlin names in `suite-coil` |
| `SuiteMediaPlayerPage`, `SuiteMediaRequest` in `suite` | same Kotlin names in `suite-media3` |
| `TimberAppLogger`, `TimberInitializer` in `suite` | same Kotlin names in `suite-log-timber` |

The `suite-*` Umeng analytics, U-APM and EMAS Maven artifact IDs stay the same;
their Gradle project paths now nest under `:suite:*`. Existing `1.0.0`
binaries remain available for consumers not yet migrated.

## License

HiAndroid is licensed under the
[Apache License 2.0](LICENSE).

## Optional U-Share WeChat platform

`:suite:socialsharing-umeng-wechat` is consumed through the parent composite
build as `com.tospery:suite-socialsharing-umeng-wechat`; it is not in the
public publication whitelist.
It adds U-Share 7.3.7's WeChat handler and WeChat OpenSDK 6.8.24 without forcing
those dependencies on SMS/email-only hosts. The host provides its public AppID,
a narrow FileProvider authority and a callback activity at
`<applicationId>.wxapi.WXEntryActivity` extending
`UmengSocialSharingWeChatCallbackActivity`.
The callback readiness check must reflect the host's consent-aware runtime.
Sharing uses an empty AppSecret; authorization and secret exchange belong on a server.

`SocialSharingContent.image` accepts portable HTTPS or local-file sources. Image selection,
resource loading and app-specific thumbnail policy stay in the host application.
