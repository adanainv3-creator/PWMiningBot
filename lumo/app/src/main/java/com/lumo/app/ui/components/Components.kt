package com.lumo.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lumo.app.data.model.Song
import com.lumo.app.ui.theme.*
import com.lumo.app.utils.PlayerState
import kotlin.math.sin

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0x1AFFFFFF),
                        Color(0x0AFFFFFF),
                    )
                ),
                shape = shape
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0x33FFFFFF),
                        Color(0x0AFFFFFF),
                    )
                ),
                shape = shape
            ),
        content = content
    )
}

@Composable
fun NeonGlowButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .shadow(
                elevation = 0.dp,
                shape = RoundedCornerShape(50),
            ),
        colors = ButtonDefaults.buttonColors(
            containerColor = NeonGreen,
            contentColor = DeepBlack,
            disabledContainerColor = DividerGray,
            disabledContentColor = TextTertiary,
        ),
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
        content = content,
    )
}

@Composable
fun AlbumArtwork(
    uri: Any?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    placeholder: @Composable (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(CardBlack),
        contentAlignment = Alignment.Center,
    ) {
        if (uri != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(uri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            placeholder?.invoke() ?: DefaultAlbumPlaceholder()
        }
    }
}

@Composable
fun DefaultAlbumPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(ElevatedBlack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(32.dp),
        )
    }
}

@Composable
fun SongListItem(
    song: Song,
    isPlaying: Boolean = false,
    onSongClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(52.dp)) {
            AlbumArtwork(
                uri = song.artworkUri,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(10.dp),
            )
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x99000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    PlayingBarsIndicator()
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isPlaying) NeonGreen else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${song.artist} • ${song.album}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = formatDuration(song.duration),
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
        )
        IconButton(onClick = onMoreClick, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = TextTertiary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun PlayingBarsIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "bars")
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f, label = "b1",
        animationSpec = infiniteRepeatable(tween(400), RepeatMode.Reverse)
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0.7f, targetValue = 0.3f, label = "b2",
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse)
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.4f, label = "b3",
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse)
    )
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(bar1, bar2, bar3).forEach { height ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((14 * height).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(NeonGreen)
            )
        }
    }
}

@Composable
fun WaveformVisualizer(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    accentColor: Color = NeonGreen,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        label = "phase",
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing))
    )
    val amplitude by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.15f,
        animationSpec = tween(600),
        label = "amplitude"
    )

    Canvas(modifier = modifier) {
        val barCount = 48
        val barWidth = size.width / (barCount * 2f)
        val centerY = size.height / 2f

        for (i in 0 until barCount) {
            val x = i * (size.width / barCount) + barWidth / 2f
            val wave1 = sin(i * 0.4f + phase).toFloat()
            val wave2 = sin(i * 0.7f + phase * 1.3f).toFloat()
            val barHeight = ((wave1 * 0.6f + wave2 * 0.4f).coerceIn(-1f, 1f)) * (size.height * 0.45f) * amplitude
            val alpha = (0.4f + 0.6f * kotlin.math.abs(wave1)).coerceIn(0.3f, 1f)

            drawRoundRect(
                color = accentColor.copy(alpha = alpha),
                topLeft = Offset(x - barWidth / 2f, centerY - kotlin.math.abs(barHeight)),
                size = Size(barWidth, kotlin.math.abs(barHeight) * 2f),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

@Composable
fun MiniPlayerBar(
    state: PlayerState,
    onPlayerClick: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = state.currentSong ?: return

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(onClick = onPlayerClick),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(modifier = Modifier.size(48.dp)) {
                AlbumArtwork(
                    uri = song.artworkUri,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(10.dp),
                )
                if (state.isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x80000000)),
                        contentAlignment = Alignment.Center,
                    ) {
                        PlayingBarsIndicator()
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onPlayPause) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    tint = NeonGreen,
                    modifier = Modifier.size(28.dp),
                )
            }
            IconButton(onClick = onSkipNext) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Skip Next",
                    tint = TextSecondary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        if (state.duration > 0L) {
            LinearProgressIndicator(
                progress = { (state.position.toFloat() / state.duration.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.BottomStart),
                color = NeonGreen,
                trackColor = DividerGray,
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
        )
        if (actionLabel != null && onActionClick != null) {
            TextButton(onClick = onActionClick) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonGreen,
                )
            }
        }
    }
}

@Composable
fun EmptyStateView(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(ElevatedBlack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        if (action != null) {
            Spacer(Modifier.height(32.dp))
            action()
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
fun LumoTopBar(
    title: String,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBackClick != null) {
            IconButton(onClick = onBackClick) {
                Icon(
                    Icons.Rounded.ArrowBackIosNew,
                    contentDescription = "Back",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            Spacer(Modifier.width(48.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Row(content = actions)
    }
}
