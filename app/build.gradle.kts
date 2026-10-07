import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "app.mosaic"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.mosaic"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "1.6.1"
        vectorDrawables.useSupportLibrary = true
    }

    // Releases must always be signed with the same key, or Android refuses to update the app.
    // CI provides it through the MOSAIC_* environment variables (from repository secrets);
    // local and PR builds without them fall back to the debug key.
    val releaseKeystore = System.getenv("MOSAIC_KEYSTORE")?.takeIf { it.isNotBlank() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("MOSAIC_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MOSAIC_KEY_ALIAS")
                keyPassword = System.getenv("MOSAIC_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests {
            // Robolectric renders the real Compose UI for the documentation screenshots.
            isIncludeAndroidResources = true
            all {
                it.systemProperty("robolectric.graphicsMode", "NATIVE")
                it.systemProperty("roborazzi.test.record", "true")
                it.maxHeapSize = "4g"
                // Fresh process per test class: app singletons (DataStore) must not leak between them.
                it.forkEvery = 1
                it.testLogging {
                    events("failed")
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                    showStackTraces = true
                }
            }
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.cast)
    implementation(libs.androidx.mediarouter)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.media3.datasource.okhttp)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.core)

    // Documentation screenshots (./gradlew :app:testDebugUnitTest --tests '*Screenshots*')
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
