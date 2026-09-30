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
import androidx.compose.ui.text.style.TextOverflow
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
    "romance" to Color(0xFFFF4F81),
    "fantasy" to Color(0xFF8E44EC),
    "thriller" to Color(0xFFE53935),
    "action" to Color(0xFFFF7043),
    "comedy" to Color(0xFFF5A623),
    "drama" to Color(0xFF5C6BC0),
    "mystery" to Color(0xFF3F51B5),
    "slice of life" to Color(0xFF26A69A),
)

fun genreColor(genre: String?): Color = genreColors[genre?.lowercase()] ?: Green

@Composable
fun Cover(url: String?, description: String?, modifier: Modifier = Modifier) {
    AsyncImage(
        model = url,
        contentDescription = description,
        contentScale = ContentScale.Crop,
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
        Cover(series.coverUrl, series.title, Modifier.fillMaxWidth().aspectRatio(1f))
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            GenreLabel(series.genre)
            Text(
                series.title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            HeartCount(series.follows)
        }
    }
}
