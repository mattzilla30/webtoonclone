package com.webtoonclone.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Error(val message: String) : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
}

@Composable
fun <T> LoadView(state: Load<T>, onRetry: () -> Unit, content: @Composable (T) -> Unit) {
    when (state) {
        Load.Loading -> SkeletonList()

        is Load.Error -> Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(state.message)
            Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) { Text("Retry") }
        }

        is Load.Ready -> content(state.value)
    }
}

/** Turns a failure into a message a reader can act on, instead of an exception string. */
fun friendlyError(e: Throwable, fallback: String): String {
    val message = e.message.orEmpty()
    return when {
        e is UnknownHostException || e is ConnectException || e is SocketTimeoutException ->
            "No connection. Check your internet and try again."
        Regex("""\b429\b""").containsMatchIn(message) -> "MangaDex is busy right now. Try again in a moment."
        Regex("""HTTP 5\d\d""").containsMatchIn(message) -> "MangaDex is having trouble. Try again in a moment."
        e is IOException && message.startsWith("MangaDex") -> "MangaDex could not load this. Try again."
        else -> fallback
    }
}

/** Placeholder rows shaped like a list of series, pulsing while content loads. */
@Composable
fun SkeletonList(rows: Int = 8) {
    val pulse = rememberInfiniteTransition(label = "skeleton")
    val alpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "skeleton-alpha",
    )
    val block = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
    val shape = RoundedCornerShape(4.dp)
    Column(
        Modifier.fillMaxSize().padding(16.dp).semantics { contentDescription = "Loading" },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(rows) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(48.dp).aspectRatio(2f / 3f).background(block, shape))
                Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth(0.6f).height(14.dp).background(block, shape))
                    Box(Modifier.fillMaxWidth(0.35f).height(10.dp).background(block, shape))
                }
            }
        }
    }
}
