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
| `:core` | `com.tospery:core:0.0.2` | AAR | Reusable Android and Jetpack Compose core utilities. |
| `:nav` | `com.tospery:nav:0.0.2` | JAR | Platform-independent URL and URI navigation abstractions. |
| `:net` | `com.tospery:net:0.0.2` | JAR | Platform-independent networking contracts and models. |
| `:net:retrofit` | `com.tospery:net-retrofit:0.0.2` | JAR | Retrofit, OkHttp and Moshi implementation for `:net`. |
| `:suite` | `com.tospery:suite:0.0.2` | AAR | Reusable Android app utilities and Compose components. |
| `:github:model:core` | `com.tospery:github-model-core:0.0.2` | JAR | Stable models shared by GitHub integrations. |
| `:github:trending` | `com.tospery:github-trending:0.0.2` | JAR | GitHub Trending URL building, fetching and HTML parsing. |

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
