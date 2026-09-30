package com.webtoonclone.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        Load.Loading -> Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { CircularProgressIndicator() }

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

