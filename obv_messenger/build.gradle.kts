// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.navigation.safeargs) apply false
    alias(libs.plugins.oss.licenses) apply false

    // protobuf plugin for Web Client
    alias(libs.plugins.protobuf) apply false

    alias(libs.plugins.google.services) apply false
    
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

allprojects {
    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(listOf("-Xmaxerrs", "4000", "-Xmaxwarns", "4000"))
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
