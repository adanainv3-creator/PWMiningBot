package com.lumo.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.lumo.app.ui.components.*
import com.lumo.app.ui.navigation.Screen
import com.lumo.app.ui.theme.*
import com.lumo.app.viewmodel.MainViewModel

@Composable
fun QueueScreen(
    navController: NavController,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val state by viewModel.playerState.collectAsStateWithLifecycle()
    val queue = state.queue

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
    ) {
        LumoTopBar(title = "Up Next", onBackClick = { navController.popBackStack() })

        if (queue.isEmpty()) {
            EmptyStateView(
                icon = Icons.Rounded.QueueMusic,
                title = "Queue is Empty",
                subtitle = "Play a song to start filling the queue",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                "${queue.size} songs in queue",
                style = MaterialTheme.typography.labelMedium,
                color = TextTertiary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn {
                itemsIndexed(queue, key = { _, s -> s.id }) { index, song ->
                    val isCurrent = index == state.currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isCurrent) NeonGreenGlow else Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isCurrent) NeonGreen else TextTertiary,
                            modifier = Modifier.width(24.dp),
                        )
                        AlbumArtwork(
                            uri = song.artworkUri,
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(8.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                song.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isCurrent) NeonGreen else TextPrimary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                song.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (isCurrent) {
                            PlayingBarsIndicator()
                        }
                    }
                    if (index < queue.lastIndex) {
                        HorizontalDivider(
                            color = DividerGray.copy(0.3f),
                            modifier = Modifier.padding(horizontal = 88.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EqualizerScreen(
    navController: NavController,
) {
    val bands = remember {
        listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
    }
    val bandValues = remember { mutableStateListOf(0f, 0f, 0f, 0f, 0f) }
    var presetIndex by remember { mutableIntStateOf(0) }
    val presets = listOf("Flat", "Rock", "Pop", "Jazz", "Classical", "Bass Boost", "Treble Boost")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
            .verticalScroll(rememberScrollState())
    ) {
        LumoTopBar(title = "Equalizer", onBackClick = { navController.popBackStack() })

        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Preset", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presets) { preset ->
                        val selected = presets.indexOf(preset) == presetIndex
                        FilterChip(
                            selected = selected,
                            onClick = { presetIndex = presets.indexOf(preset) },
                            label = { Text(preset) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonGreen,
                                selectedLabelColor = DeepBlack,
                                containerColor = ElevatedBlack,
                                labelColor = TextSecondary,
                            ),
                        )
                    }
                }
            }
        }

        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Bands", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    bands.forEachIndexed { i, band ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                "${if (bandValues[i] >= 0) "+" else ""}${bandValues[i].toInt()}dB",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (bandValues[i] != 0f) NeonGreen else TextTertiary,
                            )
                            Spacer(Modifier.height(8.dp))
                            Slider(
                                value = bandValues[i],
                                onValueChange = { bandValues[i] = it },
                                valueRange = -12f..12f,
                                modifier = Modifier
                                    .height(140.dp)
                                    .width(32.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = NeonGreen,
                                    activeTrackColor = NeonGreen,
                                    inactiveTrackColor = DividerGray,
                                ),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(band, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                        }
                    }
                }
            }
        }

        TextButton(
            onClick = { bandValues.forEachIndexed { i, _ -> bandValues[i] = 0f }; presetIndex = 0 },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(16.dp),
        ) {
            Text("Reset to Default", color = TextSecondary)
        }
    }
}

@Composable
fun SleepTimerScreen(
    navController: NavController,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val state by viewModel.playerState.collectAsStateWithLifecycle()
    val options = listOf(5L to "5 min", 10L to "10 min", 15L to "15 min", 30L to "30 min", 45L to "45 min", 60L to "1 hour", 90L to "1.5 hours")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
    ) {
        LumoTopBar(title = "Sleep Timer", onBackClick = { navController.popBackStack() })

        if (state.isSleepTimerActive) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Timer Active", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        formatDuration(state.sleepTimerRemaining),
                        style = MaterialTheme.typography.displaySmall,
                        color = NeonGreen,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Music will stop after this time", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                    Spacer(Modifier.height(20.dp))
                    OutlinedButton(
                        onClick = { viewModel.playerController.cancelSleepTimer() },
                        border = BorderStroke(1.dp, CrimsonRed),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonRed),
                        shape = RoundedCornerShape(50),
                    ) {
                        Text("Cancel Timer")
                    }
                }
            }
        } else {
            Text(
                "Set a timer to stop music automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            options.forEach { (minutes, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.playerController.setSleepTimer(minutes * 60 * 1000L) }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ElevatedBlack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Timer, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                    }
                    Text(label, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Rounded.ChevronRight, null, tint = TextTertiary)
                }
                HorizontalDivider(color = DividerGray.copy(0.4f), modifier = Modifier.padding(horizontal = 20.dp))
            }
        }
    }
}

@Composable
fun SettingsScreen(
    navController: NavController,
) {
    var notificationsEnabled by remember { mutableStateOf(true) }
    var highQualityAudio by remember { mutableStateOf(false) }
    var crossfadeEnabled by remember { mutableStateOf(false) }
    var crossfadeDuration by remember { mutableFloatStateOf(3f) }
    var replayGainEnabled by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
            .verticalScroll(rememberScrollState())
    ) {
        LumoTopBar(title = "Settings", onBackClick = { navController.popBackStack() })

        SettingsSection("Playback") {
            SettingsToggle("High Quality Audio", "Use highest bitrate when available", Icons.Rounded.HighQuality, highQualityAudio) { highQualityAudio = it }
            SettingsToggle("Crossfade", "Blend songs smoothly", Icons.Rounded.Tune, crossfadeEnabled) { crossfadeEnabled = it }
            if (crossfadeEnabled) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text("Crossfade: ${crossfadeDuration.toInt()}s", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Slider(
                        value = crossfadeDuration,
                        onValueChange = { crossfadeDuration = it },
                        valueRange = 1f..12f,
                        steps = 10,
                        colors = SliderDefaults.colors(thumbColor = NeonGreen, activeTrackColor = NeonGreen, inactiveTrackColor = DividerGray),
                    )
                }
            }
            SettingsToggle("Replay Gain", "Normalize volume between tracks", Icons.Rounded.VolumeUp, replayGainEnabled) { replayGainEnabled = it }
        }

        SettingsSection("Notifications") {
            SettingsToggle("Media Notifications", "Show playback controls in notifications", Icons.Rounded.Notifications, notificationsEnabled) { notificationsEnabled = it }
        }

        SettingsSection("Library") {
            SettingsAction("Rescan Library", "Refresh your music library", Icons.Rounded.Refresh) {}
            SettingsAction("Clear Recently Played", "Remove playback history", Icons.Rounded.History) {}
        }

        SettingsSection("About") {
            SettingsAction("About Lumo", "Version info, credits", Icons.Rounded.Info) {
                navController.navigate(Screen.About.route)
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = NeonGreen,
        modifier = Modifier.padding(horizontal = 20.dp, top = 24.dp, bottom = 8.dp),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardBlack),
        content = content,
    )
}

@Composable
private fun SettingsToggle(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
        }
        Switch(
            checked = checked, onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = DeepBlack,
                checkedTrackColor = NeonGreen,
                uncheckedThumbColor = TextTertiary,
                uncheckedTrackColor = ElevatedBlack,
            )
        )
    }
}

@Composable
private fun SettingsAction(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = TextTertiary)
    }
}

@Composable
fun AboutScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LumoTopBar(title = "About", onBackClick = { navController.popBackStack() })
        Spacer(Modifier.height(40.dp))

        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(colors = listOf(NeonGreen, NeonGreenDim))
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "L",
                style = MaterialTheme.typography.displaySmall,
                color = DeepBlack,
            )
        }

        Spacer(Modifier.height(20.dp))
        Text("Lumo", style = MaterialTheme.typography.headlineLarge, color = TextPrimary)
        Text("Version 1.0.0", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.height(8.dp))
        Text("Your music, elevated.", style = MaterialTheme.typography.bodySmall, color = NeonGreen)

        Spacer(Modifier.height(40.dp))

        listOf(
            "Local Playback Only" to "Lumo plays music stored on your device — no internet, no accounts.",
            "Privacy First" to "Your listening data never leaves your phone.",
            "Built for Android" to "Optimized for the best audio experience on Android.",
        ).forEach { (title, desc) ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBlack)
                    .padding(16.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = NeonGreen)
                Spacer(Modifier.height(4.dp))
                Text(desc, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}
