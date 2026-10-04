import org.gradle.api.attributes.Bundling

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.dexter"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.dexter"
        minSdk = 37
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        // 64-bit ARM only. Emulators on x86_64 hosts need an arm64 system image.
        ndk { abiFilters += "arm64-v8a" }
    }

    // The interface is English only, so leave out the translations that libraries bring.
    androidResources {
        localeFilters += "en"
    }

    packaging {
        resources {
            // Licence texts and tool markers that the app never reads at run time.
            // Kotlin's builtins metadata serves kotlin-reflect, which the app does not use.
            excludes += listOf("META-INF/androidx/**/LICENSE.txt", "META-INF/*.version", "DebugProbesKt.bin", "kotlin/**.kotlin_builtins")
        }
        // Native libraries ship compressed: a smaller APK to download, unpacked once at install.
        jniLibs { useLegacyPackaging = true }
    }

    buildTypes {
        release {
            // Personal build: the debug key signs it, so it installs without any keystore setup.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures { compose = true }

    // The dependency list Google Play reads is of no use to a personal build, so it stays out of the APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        // The app ships arm64-v8a only, by design, so the missing x86_64 support is expected.
        disable += "ChromeOsAbiSupport"
    }

    testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin {
    compilerOptions {
        // Material 3 Expressive is marked experimental while it settles. The whole app uses it.
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
    }
}

// The app's data classes never change after they are made, and neither do the lists inside them. Telling the
// Compose compiler so lets a screen skip redrawing when the data it was handed is the same as last time.
composeCompiler {
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose-stability.conf"))
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.core)
    // The newer screens use icons outside the core set. R8 keeps only the ones the app draws.
    implementation(libs.compose.icons.extended)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.androidx.profileinstaller)
    testImplementation(libs.koin.test)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Tesseract (Apache 2.0) for the reader's Recognize text lookup. The language data ships in assets/tessdata.
    implementation("com.github.adaptech-cz.Tesseract4Android:tesseract4android-openmp:4.9.0")

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
    // Formatting rewrites the sources, so it finishes first when both run in one build.
    mustRunAfter("ktlintFormat")
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

// The same goes for anything that reads the sources: format them before they are compiled.
tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach { mustRunAfter("ktlintFormat") }

// Turning the live API tests on or off changes what the tests do, so Gradle must not reuse a result from the other mode.
tasks.withType<Test>().configureEach {
    inputs.property("live", providers.environmentVariable("LIVE").orElse(""))
}
