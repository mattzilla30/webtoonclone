package com.webtoonclone.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.webtoonclone.data.SeriesSummary
import com.webtoonclone.ui.theme.Green

fun compact(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fK".format(n / 1_000.0)
    else -> n.toString()
}

private val genreColors = mapOf(
    "action" to Color(0xFFFF7043),
    "adventure" to Color(0xFF26A69A),
    "boys' love" to Color(0xFF42A5F5),
    "comedy" to Color(0xFFF5A623),
    "crime" to Color(0xFF8D6E63),
    "drama" to Color(0xFF5C6BC0),
    "fantasy" to Color(0xFF8E44EC),
    "girls' love" to Color(0xFFEC407A),
    "historical" to Color(0xFFA1887F),
    "horror" to Color(0xFF7B1FA2),
    "isekai" to Color(0xFF00ACC1),
    "magical girls" to Color(0xFFF06292),
    "mecha" to Color(0xFF546E7A),
    "medical" to Color(0xFF26C6DA),
    "mystery" to Color(0xFF3F51B5),
    "philosophical" to Color(0xFF78909C),
    "psychological" to Color(0xFF6A1B9A),
    "romance" to Color(0xFFFF4F81),
    "sci-fi" to Color(0xFF1E88E5),
    "slice of life" to Color(0xFF66BB6A),
    "sports" to Color(0xFFEF6C00),
    "superhero" to Color(0xFFD32F2F),
    "thriller" to Color(0xFFE53935),
    "tragedy" to Color(0xFF616161),
    "wuxia" to Color(0xFFC0A060),
)

fun genreColor(genre: String?): Color = genreColors[genre?.lowercase()] ?: Green

@Composable
fun Cover(url: String?, description: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit) {
    AsyncImage(
        model = url,
        contentDescription = description,
        contentScale = contentScale,
        modifier = modifier,
    )
}

@Composable
fun GenreLabel(genre: String?) {
    if (genre == null) return
    Text(genre, color = genreColor(genre), fontSize = 11.sp, fontWeight = FontWeight.Medium)
}

@Composable
fun HeartCount(count: Int?) {
    if (count == null) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Favorite, contentDescription = null, tint = Green, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(3.dp))
        Text(compact(count), fontSize = 11.sp, color = Green)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        if (onClick != null) Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
    }
}

/** Square cover with genre, title, and follower count underneath. */
@Composable
fun PickTile(series: SeriesSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clickable(onClick = onClick)) {
        Cover(series.coverUrl, series.title, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            GenreLabel(series.genre)
            Text(
                series.title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
            HeartCount(series.follows)
        }
    }
}

/** "5 min ago", "3 h ago", "2 d ago", or a date for anything older than a month. */
fun timeAgo(iso: String, now: java.time.Instant = java.time.Instant.now()): String {
    val time = runCatching { java.time.OffsetDateTime.parse(iso).toInstant() }.getOrNull() ?: return ""
    return timeAgo(time, now)
}

fun timeAgo(time: java.time.Instant, now: java.time.Instant = java.time.Instant.now()): String {
    val minutes = java.time.Duration.between(time, now).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 60 * 24 -> "${minutes / 60} h ago"
        minutes < 60 * 24 * 30 -> "${minutes / (60 * 24)} d ago"
        else -> java.time.LocalDate.ofInstant(time, java.time.ZoneId.systemDefault()).toString()
    }
}
