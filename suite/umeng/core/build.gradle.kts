plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.tospery.suite.umeng.core"
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
    implementation(project(":base"))

    implementation(libs.umeng.common)
    implementation(libs.umeng.asms)

    testImplementation(libs.junit)
}
