// Wear OS companion module. New dependencies use the version catalog (libs.*) to stay aligned
// with the app; the wear-compose and play-services-wearable pins below are the remaining
// hardcoded versions to align. Register WearProgressListener in the wear manifest (see the
// integration snippet in the task report).
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
    implementation(platform(libs.compose.bom))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.wear.compose:compose-material:$wearComposeVersion")
    implementation("com.google.android.gms:play-services-wearable:$wearableVersion")
}
