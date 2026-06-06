package dev.jisungbin.copyurl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val WindowShape = RoundedCornerShape(16.dp)
private val CardShape = RoundedCornerShape(14.dp)
private val Background = Color(0xFFF1F5F9)
private val CardBackground = Color.White
private val TitleColor = Color(0xFF0F172A)
private val SubtleText = Color(0xFF64748B)
private val UrlText = Color(0xFF1E293B)
private val CleanGreen = Color(0xFF059669)
private val FullBlue = Color(0xFF2563EB)

@Composable
fun HistoryWindow(
    entries: List<CopiedUrlEntry>,
    onCopy: (String) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    MaterialTheme {
        // transparent 윈도우 + 둥근 클립 → macOS 가 불투명 콘텐츠 모양대로 그림자를 그린다.
        Box(
            modifier = modifier
                .fillMaxSize()
                .clip(WindowShape)
                .background(Background),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Header(count = entries.size)
                if (entries.isEmpty()) {
                    EmptyHistory()
                } else {
                    val showTopFade by remember { derivedStateOf { listState.canScrollBackward } }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (showTopFade) Modifier.topFadingEdge() else Modifier),
                        // 카드 그림자가 LazyColumn clip 경계에서 잘리지 않도록 안쪽 여백 확보
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(entries, key = CopiedUrlEntry::url) { entry ->
                            HistoryItem(entry = entry, onClick = { onCopy(entry.url) })
                        }
                    }
                }
            }
        }
    }
}

/** 상단을 투명→불투명으로 알파 마스킹해 스크롤 콘텐츠가 페이드되며 사라지게 한다. */
private fun Modifier.topFadingEdge(fadeHeight: Dp = 28.dp) = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black),
                startY = 0f,
                endY = fadeHeight.toPx(),
            ),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
private fun Header(count: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(text = "복사 기록", color = TitleColor, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(text = "최근 ${count}개 URL", color = SubtleText, fontSize = 12.sp)
    }
}

@Composable
private fun EmptyHistory() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "아직 복사한 URL이 없습니다", color = SubtleText, fontSize = 14.sp)
    }
}

@Composable
private fun HistoryItem(entry: CopiedUrlEntry, onClick: () -> Unit) {
    val accent = if (entry.cleaned) CleanGreen else FullBlue
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(42.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accent),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = entry.label, color = accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = entry.copiedAt, color = SubtleText, fontSize = 11.sp)
                }
                Text(
                    text = entry.url,
                    color = UrlText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
