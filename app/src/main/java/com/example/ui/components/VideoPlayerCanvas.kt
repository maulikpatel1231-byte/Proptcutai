package com.example.ui.components

import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.RenderEffect
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeColorFilter
import androidx.compose.ui.graphics.graphicsLayer
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
import com.example.model.TransitionType
import com.example.model.VideoCut
import com.example.model.VideoEditPlan
import com.example.model.VideoFilter
import com.example.ui.theme.AccentElectricCyan
import com.example.ui.theme.AccentNeonPink
import com.example.ui.theme.PrimaryNeonPurple
import com.example.ui.theme.StudioCardBgElevated
import com.example.ui.theme.StudioDarkBg
import com.example.ui.theme.TextMuted
import com.example.util.FilterMatrixHelper
import java.util.Locale
import kotlin.math.abs

@Composable
fun VideoPlayerCanvas(
    aspectRatio: AspectRatio,
    activeFilter: VideoFilter,
    filterIntensity: Float,
    editPlan: VideoEditPlan?,
    activeCut: VideoCut?,
    activeCaption: CaptionOverlay?,
    mediaFiles: List<MediaFileItem>,
    isPlaying: Boolean,
    playbackPositionSec: Float,
    totalDurationSec: Float,
    isAudioMuted: Boolean,
    onTogglePlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onSplitAtPlayhead: () -> Unit,
    onEditCaption: (CaptionOverlay) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeClip = if (activeCut != null && activeCut.clipIndex in mediaFiles.indices) {
        mediaFiles[activeCut.clipIndex]
    } else {
        mediaFiles.firstOrNull()
    }

    // Compute local elapsed seconds inside the currently playing cut for transitions & seek sync
    val cutTiming = remember(editPlan, activeCut, playbackPositionSec) {
        if (editPlan == null || activeCut == null) {
            Triple(0f, 1, 1)
        } else {
            var accum = 0f
            var foundLocal = 0f
            var foundIdx = 0
            for ((idx, c) in editPlan.cuts.withIndex()) {
                if (c.id == activeCut.id) {
                    foundLocal = (playbackPositionSec - accum).coerceAtLeast(0f)
                    foundIdx = idx
                    break
                }
                accum += c.durationSec
            }
            Triple(foundLocal, foundIdx + 1, editPlan.cuts.size)
        }
    }
    val cutLocalSec = cutTiming.first
    val cutOrderNumber = cutTiming.second
    val cutTotalCount = cutTiming.third

    val targetSourceMs = remember(activeCut, cutLocalSec) {
        if (activeCut == null) 0
        else ((activeCut.startTimeSec + cutLocalSec * activeCut.playbackSpeed) * 1000f).toInt().coerceAtLeast(0)
    }

    // Shared color grading matrix
    val androidColorMatrix = remember(activeFilter, filterIntensity) {
        FilterMatrixHelper.buildColorMatrix(activeFilter, filterIntensity)
    }
    val composeColorFilter = remember(androidColorMatrix) {
        ColorMatrixColorFilter(androidColorMatrix).asComposeColorFilter()
    }

    // Transition animation values for first 0.45s of each cut
    val transDuration = 0.45f
    val transProgress = (cutLocalSec / transDuration).coerceIn(0f, 1f)
    val activeTransition = activeCut?.transitionToNext ?: TransitionType.CROSS_DISSOLVE
    val isInTransition = cutLocalSec < transDuration && cutOrderNumber > 1

    val animScale = when {
        isInTransition && activeTransition == TransitionType.ZOOM_IN -> 1.18f - 0.18f * transProgress
        isInTransition && activeTransition == TransitionType.GLITCH_TRANSITION -> 1.05f
        activeClip?.type == MediaType.IMAGE && activeCut != null -> {
            1.0f + (cutLocalSec / activeCut.durationSec.coerceAtLeast(1f)).coerceIn(0f, 1f) * 0.08f
        }
        else -> 1.0f
    }
    val animAlpha = when {
        isInTransition && activeTransition == TransitionType.CROSS_DISSOLVE -> 0.35f + 0.65f * transProgress
        else -> 1.0f
    }
    val fadeBlackOverlayAlpha = when {
        isInTransition && activeTransition == TransitionType.FADE_BLACK -> (1f - transProgress) * 0.85f
        else -> 0f
    }
    val animTranslateX = when {
        isInTransition && activeTransition == TransitionType.SLIDE_LEFT -> (1f - transProgress) * 160f
        isInTransition && activeTransition == TransitionType.GLITCH_TRANSITION -> if ((transProgress * 10).toInt() % 2 == 0) 14f else -14f
        else -> 0f
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
                .height(310.dp)
                .background(Color.Black, RoundedCornerShape(16.dp))
                .border(1.dp, StudioCardBgElevated, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Viewport adhering to selected aspect ratio
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
                if (activeClip != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = animScale
                                scaleY = animScale
                                alpha = animAlpha
                                translationX = animTranslateX
                            }
                    ) {
                        if (activeClip.type == MediaType.VIDEO) {
                            MultiClipVideoTextureView(
                                videoUri = activeClip.uri,
                                cutId = activeCut?.id ?: activeClip.id,
                                targetSourceMs = targetSourceMs,
                                isPlaying = isPlaying,
                                playbackSpeed = activeCut?.playbackSpeed ?: 1f,
                                isMuted = isAudioMuted,
                                androidColorMatrix = androidColorMatrix,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            AsyncImage(
                                model = activeClip.uri,
                                contentDescription = activeClip.name,
                                contentScale = ContentScale.Crop,
                                colorFilter = composeColorFilter,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(text = "🎬", fontSize = 38.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Timeline is Empty",
                                fontSize = 15.sp,
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

                // Filter Atmosphere & Legacy API Color Grade Overlay
                if (activeClip != null) {
                    FilterAtmosphereOverlay(activeFilter = activeFilter, intensity = filterIntensity)
                }

                if (fadeBlackOverlayAlpha > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = fadeBlackOverlayAlpha))
                    )
                }

                // Top Left: Active Cut & AI Status Badge
                if (activeCut != null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (editPlan?.isAiGenerated == true) {
                            Surface(
                                color = AccentElectricCyan.copy(alpha = 0.88f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color(0xFF0F172A),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "AI Cut $cutOrderNumber/$cutTotalCount",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF0F172A)
                                    )
                                }
                            }
                        }

                        Surface(
                            color = Color.Black.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${activeCut.transitionToNext.icon} ${activeCut.clipName.take(12)} (${activeCut.playbackSpeed}x)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = AccentElectricCyan,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Top Right: Filter Badge & Mute Button
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = PrimaryNeonPurple.copy(alpha = 0.88f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = activeFilter.displayName,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color.Black.copy(alpha = 0.72f), CircleShape)
                            .clickable { onToggleMute() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isAudioMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                            contentDescription = if (isAudioMuted) "Unmute" else "Mute",
                            tint = if (isAudioMuted) AccentNeonPink else AccentElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Live Captions Overlay with tap-to-edit
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
                            .padding(horizontal = 16.dp, vertical = 22.dp)
                            .clickable { onEditCaption(activeCaption) }
                    ) {
                        CaptionBubble(caption = activeCaption)
                    }
                }

                // Center Play Button Overlay when paused
                if (!isPlaying && mediaFiles.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.Black.copy(alpha = 0.68f), CircleShape)
                            .border(1.5.dp, PrimaryNeonPurple, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play Video",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
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
            IconButton(
                onClick = { onSeek((playbackPositionSec - 2f).coerceAtLeast(0f)) },
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Default.FastRewind,
                    contentDescription = "Rewind 2s",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier
                    .size(48.dp)
                    .background(PrimaryNeonPurple, CircleShape)
                    .testTag("play_pause_button")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White
                )
            }

            IconButton(
                onClick = { onSeek((playbackPositionSec + 2f).coerceAtMost(totalDurationSec)) },
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = "Fast Forward 2s",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(playbackPositionSec),
                        fontSize = 11.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (isPlaying && !isAudioMuted) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = AccentElectricCyan,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(text = "Audio Track", fontSize = 9.sp, color = AccentElectricCyan)
                        }
                    }

                    Text(
                        text = formatTime(totalDurationSec),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                color = StudioCardBgElevated,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.clickable { onSplitAtPlayhead() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCut,
                        contentDescription = "Split Clip",
                        tint = AccentElectricCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Split",
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
private fun FilterAtmosphereOverlay(activeFilter: VideoFilter, intensity: Float) {
    when (activeFilter) {
        VideoFilter.RETRO_VHS -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x1AFF0055).copy(alpha = 0.12f * intensity)),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "REC ● [SP] 00:26:14",
                    color = Color.Green.copy(alpha = 0.85f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(8.dp)
                )
                Text(
                    text = "PLAY ► VHS HI-FI STEREO",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(8.dp)
                )
            }
        }
        VideoFilter.NEON_CYBER -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x2800FFFF).copy(alpha = 0.16f * intensity),
                                Color.Transparent,
                                Color(0x38FF007F).copy(alpha = 0.20f * intensity)
                            )
                        )
                    )
            )
        }
        VideoFilter.CINEMATIC_WARM -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x18FF9900).copy(alpha = 0.10f * intensity))
            )
        }
        VideoFilter.PASTEL_DREAM -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x18FFC0CB).copy(alpha = 0.10f * intensity))
            )
        }
        VideoFilter.NOIR_MONOCHROME -> {
            // Real desaturation applied via RenderEffect on API 31+, plus subtle dark vignette
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f * intensity))
                        )
                    )
            )
        }
        else -> Unit
    }
}

@Composable
private fun CaptionBubble(caption: CaptionOverlay) {
    val highlightColor = try {
        Color(android.graphics.Color.parseColor(caption.highlightColorHex))
    } catch (_: Exception) {
        Color(0xFFFFDE59)
    }

    Surface(
        color = Color.Black.copy(alpha = 0.82f),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 6.dp,
        modifier = Modifier.border(1.2.dp, highlightColor.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
    ) {
        Text(
            text = caption.text,
            color = highlightColor,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

private class TextureVideoHolder {
    var mediaPlayer: MediaPlayer? = null
    var surface: Surface? = null
    var isPrepared: Boolean = false
    var loadedUri: Uri? = null
    var lastCutId: String? = null
    var videoWidth: Int = 0
    var videoHeight: Int = 0
    var viewWidth: Int = 0
    var viewHeight: Int = 0

    fun applyCenterCrop(textureView: TextureView) {
        if (videoWidth <= 0 || videoHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) return
        val viewAspect = viewWidth.toFloat() / viewHeight.toFloat()
        val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()
        val scaleX: Float
        val scaleY: Float
        if (videoAspect > viewAspect) {
            scaleX = videoAspect / viewAspect
            scaleY = 1f
        } else {
            scaleX = 1f
            scaleY = viewAspect / videoAspect
        }
        val matrix = Matrix().apply {
            setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
        }
        textureView.setTransform(matrix)
    }

    fun release() {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {}
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        try {
            surface?.release()
        } catch (_: Exception) {}
        surface = null
        isPrepared = false
        loadedUri = null
    }
}

@Composable
private fun MultiClipVideoTextureView(
    videoUri: Uri,
    cutId: String,
    targetSourceMs: Int,
    isPlaying: Boolean,
    playbackSpeed: Float,
    isMuted: Boolean,
    androidColorMatrix: android.graphics.ColorMatrix,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val holder = remember { TextureVideoHolder() }
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }
    var surfaceReadyTick by remember { mutableIntStateOf(0) }

    // Apply API 31+ RenderEffect for true real-time color grading on video pixels
    LaunchedEffect(textureViewRef, androidColorMatrix) {
        val tv = textureViewRef ?: return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            tv.setRenderEffect(
                RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(androidColorMatrix))
            )
        }
    }

    // Load or switch video URI cleanly whenever active clip changes
    LaunchedEffect(videoUri, surfaceReadyTick) {
        val surface = holder.surface ?: return@LaunchedEffect
        if (holder.loadedUri == videoUri && holder.mediaPlayer != null) return@LaunchedEffect

        holder.isPrepared = false
        holder.loadedUri = videoUri
        holder.lastCutId = cutId

        val mp = holder.mediaPlayer ?: MediaPlayer().also { holder.mediaPlayer = it }
        try {
            mp.reset()
            mp.setSurface(surface)
            mp.setDataSource(context, videoUri)
            mp.isLooping = true
            mp.setOnVideoSizeChangedListener { _, w, h ->
                holder.videoWidth = w
                holder.videoHeight = h
                textureViewRef?.let { holder.applyCenterCrop(it) }
            }
            mp.setOnPreparedListener { preparedMp ->
                holder.isPrepared = true
                holder.videoWidth = preparedMp.videoWidth
                holder.videoHeight = preparedMp.videoHeight
                textureViewRef?.let { holder.applyCenterCrop(it) }

                val vol = if (isMuted) 0f else 1f
                preparedMp.setVolume(vol, vol)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        preparedMp.playbackParams = preparedMp.playbackParams.setSpeed(
                            playbackSpeed.coerceIn(0.25f, 3.0f)
                        )
                    } catch (_: Exception) {}
                }
                if (targetSourceMs > 0) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            preparedMp.seekTo(targetSourceMs.toLong(), MediaPlayer.SEEK_CLOSEST)
                        } else {
                            preparedMp.seekTo(targetSourceMs)
                        }
                    } catch (_: Exception) {}
                }
                if (isPlaying) {
                    preparedMp.start()
                }
            }
            mp.setOnErrorListener { _, _, _ -> true }
            mp.prepareAsync()
        } catch (_: Exception) {}
    }

    // Seek when switching cuts on the same video clip
    LaunchedEffect(cutId) {
        val mp = holder.mediaPlayer
        if (mp != null && holder.isPrepared && holder.lastCutId != cutId) {
            holder.lastCutId = cutId
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mp.seekTo(targetSourceMs.toLong(), MediaPlayer.SEEK_CLOSEST)
                } else {
                    mp.seekTo(targetSourceMs)
                }
            } catch (_: Exception) {}
        }
    }

    // Keep play/pause, speed, mute, and paused scrub in sync
    LaunchedEffect(isPlaying, playbackSpeed, isMuted, targetSourceMs) {
        val mp = holder.mediaPlayer ?: return@LaunchedEffect
        if (!holder.isPrepared) return@LaunchedEffect
        try {
            val vol = if (isMuted) 0f else 1f
            mp.setVolume(vol, vol)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val currentSpeed = mp.playbackParams.speed
                    val desired = playbackSpeed.coerceIn(0.25f, 3.0f)
                    if (abs(currentSpeed - desired) > 0.05f) {
                        mp.playbackParams = mp.playbackParams.setSpeed(desired)
                    }
                } catch (_: Exception) {}
            }

            if (isPlaying) {
                if (!mp.isPlaying) {
                    if (abs(mp.currentPosition - targetSourceMs) > 350) {
                        mp.seekTo(targetSourceMs)
                    }
                    mp.start()
                }
            } else {
                if (mp.isPlaying) {
                    mp.pause()
                }
                if (abs(mp.currentPosition - targetSourceMs) > 250) {
                    mp.seekTo(targetSourceMs)
                }
            }
        } catch (_: Exception) {}
    }

    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                        holder.viewWidth = width
                        holder.viewHeight = height
                        holder.surface = Surface(st)
                        surfaceReadyTick++
                    }

                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
                        holder.viewWidth = width
                        holder.viewHeight = height
                        holder.applyCenterCrop(this@apply)
                    }

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        holder.release()
                        return true
                    }

                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                }
                textureViewRef = this
            }
        },
        modifier = modifier
    )

    DisposableEffect(Unit) {
        onDispose {
            holder.release()
        }
    }
}

private fun formatTime(seconds: Float): String {
    val totalSec = seconds.toInt().coerceAtLeast(0)
    val mins = totalSec / 60
    val secs = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}
