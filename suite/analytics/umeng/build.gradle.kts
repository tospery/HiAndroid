plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.tospery.suite.analytics.umeng"
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

    implementation(libs.umeng.common)
    implementation(libs.umeng.asms)
    implementation(libs.umeng.uyumao)

    testImplementation(libs.junit)
}
