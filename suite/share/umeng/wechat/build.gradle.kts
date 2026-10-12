plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.tospery.suite.share.umeng.wechat"
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
    api(project(":suite-share-umeng"))

    implementation(libs.umeng.share.wx)
    implementation(libs.umeng.share.core)
    implementation(libs.wechat.sdk)
}
