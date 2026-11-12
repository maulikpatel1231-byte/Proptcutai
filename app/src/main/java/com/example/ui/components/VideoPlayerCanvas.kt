package com.example.ui.components

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asComposeColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.model.AspectRatio
import com.example.model.CaptionOverlay
import com.example.model.CaptionPosition
import com.example.model.MediaFileItem
import com.example.model.MediaType
import com.example.model.VideoCut
import com.example.model.VideoFilter
import com.example.ui.theme.AccentElectricCyan
import com.example.ui.theme.AccentNeonPink
import com.example.ui.theme.PrimaryNeonPurple
import com.example.ui.theme.StudioCardBgElevated
import com.example.ui.theme.StudioDarkBg
import com.example.ui.theme.TextMuted
import java.util.Locale

@Composable
fun VideoPlayerCanvas(
    aspectRatio: AspectRatio,
    activeFilter: VideoFilter,
    activeCut: VideoCut?,
    activeCaption: CaptionOverlay?,
    mediaFiles: List<MediaFileItem>,
    isPlaying: Boolean,
    playbackPositionSec: Float,
    totalDurationSec: Float,
    onTogglePlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeClip = if (activeCut != null && activeCut.clipIndex in mediaFiles.indices) {
        mediaFiles[activeCut.clipIndex]
    } else {
        mediaFiles.firstOrNull()
    }

    // Convert filter parameters into ColorFilter
    val colorFilter = remember(activeFilter) {
        val cm = ColorMatrix()
        cm.setSaturation(activeFilter.saturation)
        // Adjust contrast
        val scale = activeFilter.contrast
        val translate = (-0.5f * scale + 0.5f) * 255f
        val contrastMatrix = floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )
        cm.postConcat(ColorMatrix(contrastMatrix))
        ColorMatrixColorFilter(cm).asComposeColorFilter()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(StudioDarkBg)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Aspect ratio container box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(Color.Black, RoundedCornerShape(16.dp))
                .border(1.dp, StudioCardBgElevated, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Viewport adhering to selected aspect ratio (e.g. 9:16 portrait or 16:9 landscape)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .aspectRatio(aspectRatio.ratioWidth / aspectRatio.ratioHeight, matchHeightConstraintsFirst = true)
                    .background(Color(0xFF14141E))
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onTogglePlayPause()
                    },
                contentAlignment = Alignment.Center
            ) {
                // Media Display: User's Video or Photo
                if (activeClip != null) {
                    if (activeClip.type == MediaType.VIDEO) {
                        // Native Video Player directly playing user's video URI
                        NativeVideoSurface(
                            videoUri = activeClip.uri,
                            isPlaying = isPlaying,
                            playbackSpeed = activeCut?.playbackSpeed ?: 1f,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Image or photo
                        AsyncImage(
                            model = activeClip.uri,
                            contentDescription = activeClip.name,
                            contentScale = ContentScale.Crop,
                            colorFilter = colorFilter,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    // Empty state when no media files are added
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "🎬",
                                fontSize = 36.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "No Media on Timeline",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Tap '+ Add Videos & Photos' below",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                }

                // Aesthetic Filter Glitch/VHS Scanline Overlay for retro filter
                if (activeFilter == VideoFilter.RETRO_VHS) {
                    VhsRetroOverlay()
                } else if (activeFilter == VideoFilter.NEON_CYBER) {
                    NeonCyberOverlay()
                }

                // Active Transition Badge (brief notification on scene change)
                if (activeCut != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    ) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🎬 ${activeCut.clipName.take(14)} (${activeCut.playbackSpeed}x)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = AccentElectricCyan
                                )
                            }
                        }
                    }
                }

                // Filter Tag Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Surface(
                        color = PrimaryNeonPurple.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = activeFilter.displayName,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Live Captions Overlay
                if (activeCaption != null) {
                    Box(
                        modifier = Modifier
                            .align(
                                when (activeCaption.position) {
                                    CaptionPosition.TOP -> Alignment.TopCenter
                                    CaptionPosition.CENTER -> Alignment.Center
                                    else -> Alignment.BottomCenter
                                }
                            )
                            .padding(horizontal = 16.dp, vertical = 24.dp)
                    ) {
                        CaptionBubble(caption = activeCaption)
                    }
                }

                // Center Play Button Overlay when paused
                if (!isPlaying) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .border(1.5.dp, PrimaryNeonPurple, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play Video",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Playback Scrubbing Bar & Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Play / Pause Button
            IconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier
                    .size(44.dp)
                    .background(PrimaryNeonPurple, CircleShape)
                    .testTag("play_pause_button")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Time Slider
            Column(modifier = Modifier.weight(1f)) {
                Slider(
                    value = playbackPositionSec.coerceIn(0f, totalDurationSec.coerceAtLeast(1f)),
                    onValueChange = { onSeek(it) },
                    valueRange = 0f..totalDurationSec.coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = AccentElectricCyan,
                        activeTrackColor = PrimaryNeonPurple,
                        inactiveTrackColor = StudioCardBgElevated
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("timeline_seek_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(playbackPositionSec),
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = formatTime(totalDurationSec),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Aspect Ratio badge
            Surface(
                color = StudioCardBgElevated,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AspectRatio,
                        contentDescription = "Aspect Ratio",
                        tint = AccentElectricCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = aspectRatio.iconLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptionBubble(caption: CaptionOverlay) {
    Surface(
        color = Color.Black.copy(alpha = 0.8f),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 4.dp,
        modifier = Modifier.border(1.dp, PrimaryNeonPurple.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
    ) {
        Text(
            text = caption.text,
            color = try {
                Color(android.graphics.Color.parseColor(caption.highlightColorHex))
            } catch (_: Exception) {
                Color(0xFFFFDE59)
            },
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun VhsRetroOverlay() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x15FF0055)),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "REC ● [SP] 00:26:14",
            color = Color.Green.copy(alpha = 0.8f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(8.dp)
        )
        Text(
            text = "PLAY ► VHS HI-FI",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.End)
                .padding(8.dp)
        )
    }
}

@Composable
private fun NeonCyberOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0x2200FFFF),
                        Color.Transparent,
                        Color(0x33FF007F)
                    )
                )
            )
    )
}

@Composable
private fun NativeVideoSurface(
    videoUri: Uri,
    isPlaying: Boolean,
    playbackSpeed: Float,
    modifier: Modifier = Modifier
) {
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }

    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                setVideoURI(videoUri)
                setOnPreparedListener { mp ->
                    mp.isLooping = true
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            mp.playbackParams = mp.playbackParams.setSpeed(playbackSpeed.coerceIn(0.5f, 2.0f))
                        }
                    } catch (_: Exception) {}
                    if (isPlaying) {
                        start()
                    }
                }
                videoViewRef = this
            }
        },
        update = { vv ->
            if (isPlaying && !vv.isPlaying) {
                vv.start()
            } else if (!isPlaying && vv.isPlaying) {
                vv.pause()
            }
        },
        modifier = modifier
    )

    DisposableEffect(videoUri) {
        onDispose {
            videoViewRef?.stopPlayback()
        }
    }
}

private fun formatTime(seconds: Float): String {
    val totalSec = seconds.toInt().coerceAtLeast(0)
    val mins = totalSec / 60
    val secs = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}
