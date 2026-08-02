plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.tospery.suite.aliyun.emas.core"
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

    implementation(libs.aliyun.emas.apm.sdk)

    testImplementation(libs.junit)
}
