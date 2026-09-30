package com.webtoonclone.ui.reader

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.webtoonclone.ui.LoadView
import com.webtoonclone.ui.theme.Green
import kotlinx.coroutines.flow.distinctUntilChanged

private val Bar = Color(0xE6181818)

@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    seriesId: String,
    onOpenChapter: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var barsVisible by remember { mutableStateOf(true) }
    val context = LocalContext.current

    Box(Modifier.fillMaxSize().background(Color(0xFF181818))) {
        LoadView(state, onRetry = viewModel::load) { page ->
            val listState = rememberLazyListState(
                initialFirstVisibleItemIndex = page.startPage.coerceIn(0, (page.pages.size - 1).coerceAtLeast(0)),
            )

            LaunchedEffect(listState) {
                snapshotFlow { listState.firstVisibleItemIndex }
                    .distinctUntilChanged()
                    .collect { viewModel.saveProgress(it) }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { barsVisible = !barsVisible },
            ) {
                itemsIndexed(page.pages, key = { _, url -> url }) { index, url ->
                    SubcomposeAsyncImage(
                        model = url,
                        contentDescription = "Page ${index + 1}",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth(),
                        loading = { Box(Modifier.fillMaxWidth().height(500.dp).background(Color(0xFF2A2A2A))) },
                        error = { Text("Page ${index + 1} failed to load", color = Color.White, modifier = Modifier.padding(16.dp)) },
                        success = { SubcomposeAsyncImageContent() },
                    )
                }
                item {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("End of Ep. ${page.chapter.number}", color = Color.White, fontWeight = FontWeight.Bold)
                        if (page.nextId != null) {
                            Text(
                                "Next episode",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 16.dp).clip(RoundedCornerShape(22.dp)).background(Green)
                                    .clickable { onOpenChapter(page.nextId) }.padding(horizontal = 28.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            }

            if (barsVisible) {
                Row(
                    Modifier.fillMaxWidth().background(Bar).padding(horizontal = 16.dp, vertical = 12.dp).align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.clickable(onClick = onBack))
                    Text("Ep. ${page.chapter.number}", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 16.dp))
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color.White,
                        modifier = Modifier.clickable {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "https://mangadex.org/chapter/${page.chapter.id}")
                            }
                            context.startActivity(Intent.createChooser(send, null))
                        },
                    )
                }
                Row(
                    Modifier.fillMaxWidth().background(Bar).padding(horizontal = 16.dp, vertical = 10.dp).align(Alignment.BottomCenter),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val listPosition = (listState.firstVisibleItemIndex + 1).coerceAtMost(page.pages.size)
                    Text("$listPosition / ${page.pages.size}", color = Color.White, fontSize = 12.sp)
                    Text("#${page.index + 1}", color = Color.White, fontSize = 12.sp)
                    Row {
                        Icon(
                            Icons.Default.KeyboardArrowLeft,
                            contentDescription = "Previous episode",
                            tint = if (page.prevId != null) Color.White else Color.DarkGray,
                            modifier = Modifier.clickable(enabled = page.prevId != null) { onOpenChapter(page.prevId!!) },
                        )
                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = "Next episode",
                            tint = if (page.nextId != null) Color.White else Color.DarkGray,
                            modifier = Modifier.padding(start = 16.dp).clickable(enabled = page.nextId != null) { onOpenChapter(page.nextId!!) },
                        )
                    }
                }
            }
        }
    }
}
