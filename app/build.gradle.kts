import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "app.tellycine"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.tellycine"
        minSdk = 26
        // Google Play requires API 36 for new apps and updates from 31 August 2026.
        targetSdk = 36
        versionCode = 23
        versionName = "1.14.1"
        vectorDrawables.useSupportLibrary = true

        // Tellycine's own TMDB key, used unless people add theirs. CI provides it through the
        // TMDB_API_KEY environment variable (a repository secret), so it's never in the repository;
        // builds without it ask for a key of the user's own. It goes in scrambled (TmdbBuiltInKey puts
        // it back together), so it isn't a readable string in the app: obscured, not secret.
        val tmdbKey = System.getenv("TMDB_API_KEY").orEmpty().trim().toByteArray(Charsets.US_ASCII)
        val tmdbMask = MessageDigest.getInstance("SHA-512").digest("tellycine-$versionName".toByteArray())
        val scrambled = tmdbKey.mapIndexed { i, b -> (b.toInt() xor tmdbMask[i % tmdbMask.size].toInt()) and 0xFF }
        val mask = tmdbKey.indices.map { tmdbMask[it % tmdbMask.size].toInt() and 0xFF }
        buildConfigField("int[]", "TMDB_KEY_DATA", scrambled.joinToString(",", "{", "}"))
        buildConfigField("int[]", "TMDB_KEY_MASK", mask.joinToString(",", "{", "}"))
    }

    // Releases must always be signed with the same key, or Android refuses to update the app.
    // CI provides it through the TELLYCINE_* environment variables (from repository secrets);
    // local and PR builds without them fall back to the debug key.
    val releaseKeystore = System.getenv("TELLYCINE_KEYSTORE")?.takeIf { it.isNotBlank() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("TELLYCINE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TELLYCINE_KEY_ALIAS")
                keyPassword = System.getenv("TELLYCINE_KEY_PASSWORD")
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
    // Each version's schema, for writing and testing migrations.
    arg("room.schemaLocation", "$projectDir/schemas")
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
    implementation(project(":ffmpeg"))
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.cast)
    implementation(libs.androidx.mediarouter)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.session)

    // Android TV interface
    implementation(libs.androidx.tv.material)
    // QR codes, so a phone can open links shown on the TV
    implementation(libs.zxing.core)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // Colours picked from posters, to tint movie and series pages
    implementation(libs.androidx.palette)
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
