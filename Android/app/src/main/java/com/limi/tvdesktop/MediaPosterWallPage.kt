package com.limi.tvdesktop

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MediaPosterWallPage(
    category: MediaCategoryInfo,
    artwork: DemoArtwork,
    onDismiss: () -> Unit,
    onSelectMedia: (DemoMedia) -> Unit
) {
    val backButtonFocus = remember { FocusRequester() }
    val gridFocus = remember { FocusRequester() }
    val gridState = rememberLazyGridState()

    val scope = rememberCoroutineScope()
    // 7 columns at the 1672dp design width. One page = 5 rows; scrolling into the last three
    // rows of the loaded window pulls the next page.
    val columns = 7
    val pageSize = columns * 5
    val provider = remember(category) {
        MediaLibraryManager.getProviderByAccountId(category.accountId)
            ?: AccountManager.accounts.find { it.enabled }?.let { MediaLibraryManager.getProvider(it) }
    }
    var items by remember { mutableStateOf<List<MediaItemInfo>>(emptyList()) }
    var totalCount by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var filterSort by remember { mutableStateOf("默认") }

    fun loadNext() {
        if (isLoading || !hasMore) return
        isLoading = true
        val start = items.size
        scope.launch {
            val page = withContext(Dispatchers.IO) {
                runCatching { provider?.getCategoryItemsPage(category.id, start, pageSize) }.getOrNull()
            }
            if (page != null && page.items.isNotEmpty()) {
                items = items + page.items
                totalCount = page.totalCount
                hasMore = items.size < page.totalCount
            } else {
                hasMore = false
            }
            isLoading = false
        }
    }

    LaunchedEffect(category) {
        items = emptyList()
        totalCount = 0
        hasMore = true
        isLoading = false
        loadNext()
    }

    LaunchedEffect(gridState, items.size, hasMore) {
        if (!hasMore) return@LaunchedEffect
        snapshotFlow {
            val scrolled = gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0
            scrolled to (gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1)
        }.distinctUntilChanged().collect { (scrolled, last) ->
            if (scrolled && last >= items.size - columns * 3) loadNext()
        }
    }

    LaunchedEffect(Unit) {
        backButtonFocus.requestFocus()
    }

    val displayItems = remember(items, filterSort) {
        when (filterSort) {
            "最新" -> items.sortedByDescending { it.year }
            "评分" -> items.sortedByDescending { it.rating.toDoubleOrNull() ?: 0.0 }
            "标题" -> items.sortedBy { it.title }
            else -> items
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF101010))
    ) {
        // Background subtle gradient
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1A1D24), Color(0xFF111317), Color(0xFF090A0C))
                    )
                )
        )

        Column(Modifier.fillMaxSize()) {
            // Top Navigation / Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .padding(horizontal = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    // Back button
                    FocusCard(
                        modifier = Modifier
                            .height(48.dp)
                            .width(110.dp)
                            .focusRequester(backButtonFocus)
                            .focusProperties { down = gridFocus },
                        radius = 24.dp,
                        onClick = onDismiss
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color(0x33FFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("← 返回", color = White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    // Title
                    Text(
                        text = category.title,
                        color = White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Count Badge
                    if (displayItems.isNotEmpty()) {
                        Text(
                            text = "${if (totalCount > 0) totalCount else displayItems.size} 部作品",
                            color = Color(0xFF999999),
                            fontSize = 18.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x22FFFFFF))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }

                // Sorting / Filter pills
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x33444444))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("默认", "最新", "评分", "标题").forEach { sort ->
                        val isSelected = filterSort == sort
                        var isFocused by remember { mutableStateOf(false) }
                        Text(
                            text = sort,
                            color = if (isFocused) Color(0xFF111111) else if (isSelected) Color(0xFF111111) else Color(0xFFCCCCCC),
                            fontSize = 16.sp,
                            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isFocused) Color.White else if (isSelected) Color(0xFFEEEEEE) else Color.Transparent)
                                .onFocusChanged { isFocused = it.isFocused }
                                .focusProperties { down = gridFocus }
                                .focusable()
                                .clickable { filterSort = sort }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Main Body: Poster Grid or Loading / Empty state
            Box(Modifier.fillMaxSize().padding(horizontal = 48.dp)) {
                if (isLoading && items.isEmpty()) {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = Color(0xFF3875F6))
                        Spacer(Modifier.height(16.dp))
                        Text("正在拉取 ${category.title} 海报墙...", color = Color(0xFF999999), fontSize = 18.sp)
                    }
                } else if (displayItems.isEmpty()) {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("该媒体库暂无内容", color = Color(0xFF888888), fontSize = 22.sp)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 196.dp),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 64.dp),
                        verticalArrangement = Arrangement.spacedBy(28.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        itemsIndexed(displayItems, key = { _, item -> item.id }) { index, item ->
                            val demoMedia = item.toDemoMedia()
                            PosterWallCard(
                                media = demoMedia,
                                artwork = artwork,
                                onClick = { onSelectMedia(demoMedia) },
                                modifier = if (index == 0) Modifier.focusRequester(gridFocus) else Modifier
                            )
                        }
                        if (hasMore && isLoading) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    Modifier.fillMaxWidth().padding(vertical = 20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = Color(0xFF3875F6), modifier = Modifier.size(28.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PosterWallCard(
    media: DemoMedia,
    artwork: DemoArtwork,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val real = media.realItem
    val posterUrl = real?.posterUrl.orEmpty()
    val rating = real?.rating.orEmpty()
    val year = real?.year.orEmpty()

    Column(
        Modifier
            .fillMaxWidth()
            .width(196.dp)
    ) {
        FocusCard(
            modifier = modifier
                .fillMaxWidth()
                .height(294.dp),
            radius = 12.dp,
            onClick = onClick
        ) {
            val coverAlpha = LocalCardCoverAlpha.current
            val networkBitmap = if (posterUrl.isNotBlank()) rememberPosterImage(posterUrl) else null

            Image(
                bitmap = networkBitmap ?: artwork.image(media),
                contentDescription = media.title,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = coverAlpha },
                contentScale = ContentScale.Crop
            )

            // Top rating badge
            if (rating.isNotBlank() && rating != "0" && rating != "0.0") {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xD9000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "★ $rating",
                        color = Color(0xFFFFB800),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Bottom Year / Type tag
            if (year.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x99000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = year, color = Color(0xDDFFFFFF), fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = media.title,
            color = White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (media.detail.isNotBlank()) {
            Text(
                text = media.detail,
                color = Color(0xFF888888),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
