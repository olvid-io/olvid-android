plugins {
    id("com.android.library")
}

android {
    compileSdk = 36
    namespace = "io.olvid.engine"

    defaultConfig {
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // do not update further: jackson >2.13 does not work on older Android APIs
    implementation(libs.jackson.databind)
    implementation(libs.olvid.sqlite.jdbc.android)

    implementation(libs.slf4j.api)
    implementation(libs.slf4j.simple)

    implementation(libs.jose4j)

    implementation(libs.okhttp5)
    implementation(libs.iharder.base64)

    testImplementation(libs.junit)
    testImplementation(libs.sqlite.jdbc) // only here to check if a new version is available
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
