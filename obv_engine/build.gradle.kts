// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    id("com.android.library") version "9.0.0" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
