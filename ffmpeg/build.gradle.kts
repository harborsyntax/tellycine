// Media3's FFmpeg audio decoder extension (androidx/media 1.6.1, libraries/decoder_ffmpeg), which
// Google doesn't publish prebuilt: its Java classes unchanged, and libffmpegJNI.so for each ABI,
// built from FFmpeg's source by natives/build.sh (see README.md). The player uses it only for
// sound the device has no decoder of its own for, such as Dolby Digital, DTS and MPEG Layer II.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "androidx.media3.decoder.ffmpeg"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("proguard-rules.txt")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(libs.androidx.media3.decoder)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.annotation)
    compileOnly(libs.checker.qual)
}
