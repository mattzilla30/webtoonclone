package com.webtoonclone.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webtoonclone.data.GenreBand
import com.webtoonclone.data.SeriesSummary
import com.webtoonclone.ui.Cover
import com.webtoonclone.ui.GenreLabel
import com.webtoonclone.ui.LoadView
import com.webtoonclone.ui.PickTile
import com.webtoonclone.ui.SectionHeader
import com.webtoonclone.ui.genreColor

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenSeries: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenGenre: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LoadView(state, onRetry = viewModel::load) { home ->
        LazyColumn(Modifier.fillMaxSize()) {
            home.hero?.let { hero -> item { Hero(hero, onOpenSearch) { onOpenSeries(hero.id) } } }

            item { SectionHeader("New Series") }
            items(home.newSeries.size) { i ->
                NewSeriesRow(home.newSeries[i]) { onOpenSeries(home.newSeries[i].id) }
            }

            item { SectionHeader("Today's Picks") }
            items(home.picks.chunked(2).size) { row ->
                Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    home.picks.chunked(2)[row].forEach { series ->
                        PickTile(series, { onOpenSeries(series.id) }, Modifier.weight(1f))
                    }
                }
            }

            item { SectionHeader("Favorite Genres") }
            items(home.genreBands.size) { i ->
                GenreBandRow(home.genreBands[i], onOpenGenre, onOpenSeries)
            }

            item { Box(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun Hero(series: SeriesSummary, onSearch: () -> Unit, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(360.dp).clickable(onClick = onClick)) {
        Cover(series.coverUrl, series.title, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))),
            ),
        )
        Icon(
            Icons.Default.Search,
            contentDescription = "Search",
            tint = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(26.dp).clickable(onClick = onSearch),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(series.title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2)
            Text(
                series.description,
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun NewSeriesRow(series: SeriesSummary, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp).height(84.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            GenreLabel(series.genre)
            Text(series.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                series.description,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Cover(series.coverUrl, series.title, Modifier.width(96.dp).fillMaxSize().clip(RoundedCornerShape(4.dp)))
    }
}

@Composable
private fun GenreBandRow(band: GenreBand, onOpenGenre: (String) -> Unit, onOpenSeries: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(genreColor(band.genre)).padding(vertical = 12.dp),
    ) {
        Column(Modifier.clickable { onOpenGenre(band.genre) }.padding(horizontal = 16.dp)) {
            Text(band.genre, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(band.tagline, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
        }
        Row(
            Modifier.padding(horizontal = 16.dp).padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            band.series.forEach { series ->
                Cover(
                    series.coverUrl,
                    series.title,
                    Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).clickable { onOpenSeries(series.id) },
                )
            }
        }
    }
}
