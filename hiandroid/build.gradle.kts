plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.tospery.hiandroid"
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
    api(project(":core"))
    api(project(":nav"))
    api(project(":nav:compose"))
    api(project(":net"))
    api(project(":net:retrofit"))
    api(project(":store"))
    api(project(":store:datastore"))
    api(project(":store:keystore"))
    api(project(":suite"))
}
