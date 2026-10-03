// Wear OS companion module. It talks to the phone over Bluetooth (PhoneLink), with no Google
// services. New dependencies use the version catalog (libs.*) to stay aligned with the app.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val wearComposeVersion = "1.4.0"

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
}
