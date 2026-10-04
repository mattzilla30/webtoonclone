package com.dexter.ui.settings

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri

/** One open-source component Dexter ships, with its license and where to read more. */
data class OpenSourceComponent(val name: String, val license: String, val url: String)

/** Where Dexter's own source code lives. */
const val SOURCE_URL = "https://github.com/mattzilla30/Dexter"

/** Every open-source component inside the app, A to Z. Dexter's own GPL notice comes first on the page. */
val OpenSourceComponents: List<OpenSourceComponent> = listOf(
    OpenSourceComponent("Accompanist", "Apache 2.0", "https://github.com/google/accompanist"),
    OpenSourceComponent("AndroidX: Activity, Compose, Core, DataStore, Lifecycle, Navigation, Room, WorkManager, Wear Compose", "Apache 2.0", "https://developer.android.com/jetpack/androidx"),
    OpenSourceComponent("Coil", "Apache 2.0", "https://github.com/coil-kt/coil"),
    OpenSourceComponent("Guava ListenableFuture", "Apache 2.0", "https://github.com/google/guava"),
    OpenSourceComponent("JSpecify", "Apache 2.0", "https://github.com/jspecify/jspecify"),
    OpenSourceComponent("Koin", "Apache 2.0", "https://github.com/InsertKoinIO/koin"),
    OpenSourceComponent("Kotlin, Kotlin Coroutines, Kotlin Serialization", "Apache 2.0", "https://github.com/JetBrains/kotlin"),
    OpenSourceComponent("Leptonica", "BSD 2-Clause", "http://www.leptonica.org/about-the-license.html"),
    OpenSourceComponent("libjpeg", "IJG License", "https://ijg.org/"),
    OpenSourceComponent("libpng", "libpng License", "http://www.libpng.org/pub/png/src/libpng-LICENSE.txt"),
    OpenSourceComponent("Material Design icons", "Apache 2.0", "https://github.com/google/material-design-icons"),
    OpenSourceComponent("OkHttp and Okio", "Apache 2.0", "https://github.com/square/okhttp"),
    OpenSourceComponent("Protocol Buffers (inside DataStore)", "BSD 3-Clause", "https://github.com/protocolbuffers/protobuf"),
    OpenSourceComponent("Stately", "Apache 2.0", "https://github.com/touchlab/Stately"),
    OpenSourceComponent("Tesseract OCR and its language data", "Apache 2.0", "https://github.com/tesseract-ocr/tesseract"),
    OpenSourceComponent("Tesseract4Android", "Apache 2.0", "https://github.com/adaptech-cz/Tesseract4Android"),
)

/** Dexter's own license notice, then every open-source component it ships. Each row opens its page. */
@Composable
internal fun LicensesSection() {
    val context = LocalContext.current
    val open = { url: String -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } }
    Setting(
        "Open-source licenses",
        "Dexter is free software under the GNU General Public License, version 3, with ABSOLUTELY NO WARRANTY. It is built with the libraries below.",
        keywords = listOf("gpl", "source code", "about", "credits") + OpenSourceComponents.map { it.name },
    ) {
        SubLink("GNU General Public License v3", "Dexter's license") { open("https://www.gnu.org/licenses/gpl-3.0.html") }
        SubLink("Source code", SOURCE_URL.removePrefix("https://")) { open(SOURCE_URL) }
        OpenSourceComponents.forEach { component ->
            SubLink(component.name, component.license) { open(component.url) }
        }
    }
}
