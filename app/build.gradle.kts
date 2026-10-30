plugins {
    alias(libs.plugins.android.application)
}

// Keep the portable encoder/decoder core; desktop JavaSound frontends are unavailable on Android.
val mp3Encoder by configurations.creating
val androidMp3Encoder by tasks.registering(Jar::class) {
    from({ mp3Encoder.map { zipTree(it) } })
    include("de/sciss/jump3r/mp3/**", "de/sciss/jump3r/mpg/**")
    archiveFileName.set("android-mp3-encoder.jar")
    destinationDirectory.set(layout.buildDirectory.dir("mp3-encoder"))
}

android {
    namespace = "de.jeisfeld.mymeditation"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.jeisfeld.mymeditation"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    mp3Encoder("de.sciss:jump3r:1.0.5")
    implementation(files(androidMp3Encoder.flatMap { it.archiveFile }))
    implementation("androidx.media3:media3-common:1.8.0")

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.preference)
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}