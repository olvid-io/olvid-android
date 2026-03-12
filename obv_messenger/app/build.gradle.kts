import com.android.build.VariantOutput
import com.android.build.gradle.api.ApkVariantOutput
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.navigation.safeargs)
    alias(libs.plugins.oss.licenses)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.google.services)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val os: OperatingSystem? = OperatingSystem.current()

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "false")
}

ext {
    set("fdroidAbiCodes", mapOf("arm64-v8a" to 1, "armeabi-v7a" to 2, "x86_64" to 3, "x86" to  4))
}

android {
    compileSdk = 36
    namespace = "io.olvid.messenger"

    defaultConfig {
        applicationId = "io.olvid.messenger"
        minSdk = 23
        targetSdk = 36
        versionCode = 296
        versionName = "4.2.4"
        vectorDrawables.useSupportLibrary = true
        // MultiDex is enabled by default for minSdk >= 21
        androidResources {
            localeFilters.addAll(listOf("en", "fr", "af", "ar", "ca", "cs", "da", "de", "el", "es", "fa", "fi", "hi", "hr", "hu", "it", "iw", "ja", "ko", "nl", "no", "pl", "pt", "pt-rBR", "ro", "ru", "sk", "sl", "sv", "tr", "uk", "vi", "zh", "zh-rTW"))
        }
        manifestPlaceholders["appAuthRedirectScheme"] = "olvid.openid"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        /////////////
        // Switch this setting to "false" to disable SSL handshake verification altogether.
        // - when "true", Olvid will pin all SSL certificates it encounters so that it may warn the user when a certificate changes
        // - in some contexts (typically with an SSL proxy configured at the OS level) this handshake verification may cause issues
        // If unsure, leave this "true".
        buildConfigField("boolean", "ENABLE_SSL_HANDSHAKE_VERIFICATION", "true")
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(21)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    packaging {
        resources {
            excludes.add("META-INF/INDEX.LIST")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false // Default to false, handled by androidComponents
            isShrinkResources = false // Default to false, handled by androidComponents
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
        compose = true
    }

    flavorDimensions += listOf("features", "google-services")

    productFlavors {
        create("prod") {
            dimension = "features"
            buildConfigField("String", "HARDCODED_API_KEY", "null")
            buildConfigField("String", "HARDCODED_DATABASE_SECRET", "null")
            buildConfigField("String", "SERVER_NAME", "\"https://server.olvid.io\"")
            buildConfigField("String", "KEYCLOAK_REDIRECT_URL", "\"https://openid-redirect.olvid.io/\"")
            buildConfigField("int", "LOG_LEVEL", "io.olvid.engine.Logger.INFO")
            buildConfigField("String[]", "TURN_SERVERS", "new String[]{\"turn:turn.olvid.io:5349?transport=udp\", \"turn:turn.olvid.io:443?transport=tcp\", \"turns:turn.olvid.io:443?transport=tcp\"}")
            manifestPlaceholders.putAll(mapOf(
                "KEYCLOAK_REDIRECT_HOST" to "openid-redirect.olvid.io",
                "MAPS_API_KEY" to "<please fill in your own API key>"
            ))
        }

        create("full") {
            dimension = "google-services"
            buildConfigField("int", "VERSION_CODE_MULTIPLIER", "1")
            buildConfigField("boolean", "USE_BILLING_LIB", "true")
            buildConfigField("boolean", "USE_FIREBASE_LIB", "true")
            buildConfigField("boolean", "USE_GOOGLE_LIBS", "true")
        }

        create("nogoogle") {
            dimension = "google-services"
            applicationIdSuffix = ".nogoogle"
            versionNameSuffix = "-nogoogle"
            buildConfigField("int", "VERSION_CODE_MULTIPLIER", "1")
            buildConfigField("boolean", "USE_BILLING_LIB", "false")
            buildConfigField("boolean", "USE_FIREBASE_LIB", "false")
            buildConfigField("boolean", "USE_GOOGLE_LIBS", "false")
        }

        create("zfdroid") {
            dimension = "google-services"
            applicationIdSuffix = ".nogoogle"
            versionNameSuffix = "-fdroid"
            buildConfigField("int", "VERSION_CODE_MULTIPLIER", "100")
            buildConfigField("boolean", "USE_BILLING_LIB", "false")
            buildConfigField("boolean", "USE_FIREBASE_LIB", "false")
            buildConfigField("boolean", "USE_GOOGLE_LIBS", "false")
        }
    }

    // also include nogoogle source folders in the FDroid build
    sourceSets {
        named("zfdroid") {
            java.srcDirs("src/nogoogle/java")
            kotlin.srcDirs("src/nogoogle/java")
            res.srcDirs("src/nogoogle/res")
        }
    }

    splits {
        abi {
            isEnable = gradle.startParameter.taskNames.any { it.contains("zfdroid", ignoreCase = true) }
            isUniversalApk = isEnable.not()
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    applicationVariants.configureEach {
        if (flavorName.contains("zfdroid", ignoreCase = true)) {
            outputs.forEach { output ->
                val abi = output.filters.find { it.filterType == VariantOutput.FilterType.ABI.name }?.identifier
                abi?.let {
                    @Suppress("UNCHECKED_CAST")
                    val archVersionCodeOffset = (project.ext.get("fdroidAbiCodes") as? Map<String, Int>)?.get(abi) ?: 0
                    (output as ApkVariantOutput).versionCodeOverride =
                        (100 * project.android.defaultConfig.versionCode!!) + archVersionCodeOffset
                }
            }
        }
    }
}

androidComponents {
    beforeVariants { variantBuilder ->
        // Enable minification only for the 'full' flavor in release builds
        if (variantBuilder.buildType == "release") {
            val isFullFlavor = variantBuilder.productFlavors.any { it.second == "full" }
            variantBuilder.isMinifyEnabled = isFullFlavor
            variantBuilder.shrinkResources = isFullFlavor
        }
    }

    onVariants(selector().withBuildType("release")) { variant ->
        variant.packaging.resources.excludes.add("META-INF/*")
    }
}

// Fix implicit dependency error for oss-licenses-plugin in Gradle 9.1
tasks.configureEach {
    if (name.endsWith("OssLicensesCleanUp")) {
        val variantName = name.removeSuffix("OssLicensesCleanUp")
        dependsOn("${variantName}OssDependencyTask")
    }
}

composeCompiler {
    // enableStrongSkippingMode is enabled by default in Kotlin 2.0.20+
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
}

dependencies {
    testImplementation(libs.junit)

    ksp(libs.androidx.room.compiler)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

//    implementation(fileTree(mapOf("include" to listOf("*.jar", "*.aar"), "dir" to "libs")))
    implementation(project(":sardine-android"))
    implementation(project(":engine"))
    implementation(libs.olvid.webrtc.android)

    implementation(libs.google.material)
    implementation(libs.androidx.core.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.bundles.lifecycle)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.accompanist.themeadapter.appcompat)

    implementation(libs.accompanist.permissions)
    implementation(libs.reorderable)
    implementation(libs.androidx.localbroadcastmanager)
    implementation(libs.androidx.documentfile)

    implementation(libs.bundles.coil)

    implementation(libs.google.zxing)
    implementation(libs.jackson.databind)

    implementation(libs.androidx.appcompat)
    implementation(libs.bundles.emoji2)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.biometric)

    implementation(libs.bundles.camera)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.credentials)
    "fullImplementation"(libs.androidx.credentials.play.services.auth)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.fragment.compose)
    implementation(libs.androidx.localbroadcastmanager)

    implementation(libs.bundles.media3)
    implementation(libs.androidx.media)

    implementation(libs.bundles.navigation)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.bundles.room)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sharetarget)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto.ktx)
    implementation(libs.lottie.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.jsoup)

    implementation(libs.bundles.paging)

    implementation(libs.google.protobuf.lite)
    implementation(libs.appauth)
    implementation(libs.jose4j)

    implementation(libs.bundles.maplibre)
    implementation(libs.bundles.commonmark)

    "fullImplementation"(libs.google.play.billing)
    "fullImplementation"(libs.google.firebase.messaging) {
        exclude(group = "com.google.firebase", module = "firebase-core")
        exclude(group = "com.google.firebase", module = "firebase-analytics")
        exclude(group = "com.google.firebase", module = "firebase-measurement-connector")
    }
    "fullImplementation"(libs.google.play.services.maps)
    "fullImplementation"(libs.google.play.services.location)
    "fullImplementation"(libs.google.play.services.auth)
    "fullImplementation"(libs.google.http.client.gson) {
        exclude(group = "org.apache.httpcomponents")
    }
    "fullImplementation"(libs.google.api.client.android) {
        exclude(group = "org.apache.httpcomponents")
    }
    "fullImplementation"(libs.google.api.services.drive) {
        exclude(group = "org.apache.httpcomponents")
    }
    "fullImplementation"(libs.google.play.review.ktx)
    "fullImplementation"(libs.google.play.services.oss.licenses)
    "fullImplementation"(libs.google.mlkit.barcode.scanning)
    "fullImplementation"(libs.google.mlkit.text.recognition)
}

protobuf {
    protoc {
        artifact = if (os?.isMacOsX ?: false) {
            "com.google.protobuf:protoc:${libs.versions.protobuf.protoc.get()}:osx-x86_64"
        } else {
            "com.google.protobuf:protoc:${libs.versions.protobuf.protoc.get()}"
        }
    }
    plugins {
        create("javalite") {
            artifact = if (os?.isMacOsX ?: false) {
                "com.google.protobuf:protoc-gen-javalite:${libs.versions.protobuf.protocgenjavalite.get()}:osx-x86_64"
            } else {
                "com.google.protobuf:protoc-gen-javalite:${libs.versions.protobuf.protocgenjavalite.get()}"
            }
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                (this as NamedDomainObjectContainer<*>).remove("java")
            }
            task.plugins {
                create("javalite") { }
            }
        }
    }
}

// disable google services plugin task for "nogoogle" flavor
tasks.configureEach {
    if (name.startsWith("process") && name.endsWith("GoogleServices")) {
        if (name.contains("nogoogle", ignoreCase = true) || name.contains("zfdroid", ignoreCase = true)) {
            enabled = false
        }
    }
}
