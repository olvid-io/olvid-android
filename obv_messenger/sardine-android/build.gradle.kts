plugins {
    id("com.android.library")
}

android {
    compileSdk = 36
    namespace = "com.thegrizzlylabs.sardineandroid"

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("proguard-rules.pro")
    }
}

dependencies {
    api(libs.okhttp)

    implementation(libs.simple.xml) {
        exclude(module = "stax")
        exclude(module = "stax-api")
        exclude(module = "xpp3")
    }

    testImplementation(libs.junit)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
