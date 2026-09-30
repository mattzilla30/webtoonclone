import org.gradle.api.attributes.Bundling

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.webtoonclone"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.webtoonclone"
        minSdk = 37
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        // 64-bit ARM only. Emulators on x86_64 hosts need an arm64 system image.
        ndk { abiFilters += "arm64-v8a" }
    }

    // Signing reads ~/.gradle/gradle.properties or -P flags. Nothing secret lives in the repo.
    val keystorePath = providers.gradleProperty("RELEASE_STORE_FILE").orNull
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = providers.gradleProperty("RELEASE_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("RELEASE_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("RELEASE_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures { compose = true }

    lint {
        // The app ships arm64-v8a only, by design, so the missing x86_64 support is expected.
        disable += "ChromeOsAbiSupport"
    }

    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}

// Room writes its schema here so future migrations can be checked against it.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Kotlin style checks. `./gradlew :app:ktlintCheck` reports problems and `:app:ktlintFormat` fixes them.
// The rules are in the .editorconfig at the project root.
val ktlint = configurations.create("ktlint")

dependencies {
    ktlint("com.pinterest.ktlint:ktlint-cli:1.8.0") {
        attributes { attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL)) }
    }
}

val ktlintFiles = arrayOf("src/**/*.kt", "*.kts")

tasks.register<JavaExec>("ktlintCheck") {
    group = "verification"
    description = "Checks Kotlin code style with ktlint."
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args(*ktlintFiles)
}

tasks.register<JavaExec>("ktlintFormat") {
    group = "formatting"
    description = "Fixes Kotlin code style problems with ktlint."
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args("-F", *ktlintFiles)
}
