package com.webtoonclone.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webtoonclone.R
import com.webtoonclone.ui.iconTap

/** The libraries the app uses. Every one is released under the Apache License 2.0. */
private val libraries = listOf(
    "AndroidX (Compose, Navigation, Lifecycle, DataStore, WorkManager, Activity, Core)",
    "Coil",
    "OkHttp and Okio",
    "Kotlin and kotlinx.coroutines",
    "kotlinx.serialization",
)

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), modifier = Modifier.iconTap(onBack))
            Text(stringResource(R.string.credits_and_licenses), fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(start = 16.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text("Webtoon Clone $version", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                "An unofficial reader for MangaDex. It is not affiliated with MangaDex.",
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            Text(stringResource(R.string.series_and_chapters), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                "All series, covers, and chapters come from MangaDex. Credit belongs to the creators, and to the scanlation " +
                    "groups and publishers that translate and upload their work. Some series only link to the publisher's site, " +
                    "and those open in your browser.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            Text(stringResource(R.string.open_source_libraries), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(stringResource(R.string.apache_license_2_0), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
            libraries.forEach { Text(it, fontSize = 13.sp, modifier = Modifier.padding(vertical = 3.dp)) }
            Text("", modifier = Modifier.padding(bottom = 24.dp))
        }
    }
}
