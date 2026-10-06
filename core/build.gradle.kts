import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.gson)
    // Android ships its own XmlPullParser implementation; kxml2 is only needed on the JVM for compiling and tests.
    compileOnly(libs.kxml2)

    testImplementation(libs.junit)
    testImplementation(libs.kxml2)
}
