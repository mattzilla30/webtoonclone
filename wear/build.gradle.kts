// SCAFFOLD: minimal Wear OS companion module. Not compiled in this environment and not wired
// into settings.gradle.kts yet; see the integration snippet in the task report for the include
// line, the phone-side manifest entry for WearMessageReceiver, and the play-services-wearable
// dependency the app module needs.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// TODO: align these versions with the app's version catalog when wiring the module in.
val wearComposeVersion = "1.4.0"
val wearableVersion = "19.0.0"

android {
    namespace = "com.dexter.wear"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.dexter.wear"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation("androidx.wear.compose:compose-material:$wearComposeVersion")
    implementation("com.google.android.gms:play-services-wearable:$wearableVersion")
}
