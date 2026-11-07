plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.tospery.suite.socialsharing.umeng.email"
    compileSdk = 37

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":suite:socialsharing-umeng"))

    implementation(files("libs/umeng-share-email-7.3.7.jar"))
}
