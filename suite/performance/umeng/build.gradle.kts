plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.tospery.suite.performance.umeng"
    compileSdk = 37

    defaultConfig {
        minSdk = 30
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":base"))
    api(project(":suite-umeng-core"))

    implementation(libs.umeng.apm)

    testImplementation(libs.junit)
}
