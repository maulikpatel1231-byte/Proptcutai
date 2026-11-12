package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CaptionOverlay
import com.example.model.VideoCut
import com.example.model.VideoEditPlan
import com.example.ui.theme.AccentElectricCyan
import com.example.ui.theme.AccentNeonPink
import com.example.ui.theme.PrimaryNeonPurple
import com.example.ui.theme.StudioCardBg
import com.example.ui.theme.StudioCardBgElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TrackCaptionActive
import com.example.ui.theme.TrackCaptionBg
import com.example.ui.theme.TrackCutActive
import com.example.ui.theme.TrackCutBg

@Composable
fun TimelineTracksView(
    editPlan: VideoEditPlan?,
    playbackPositionSec: Float,
    totalDurationSec: Float,
    activeCut: VideoCut?,
    activeCaption: CaptionOverlay?,
    onSeek: (Float) -> Unit,
    onEditCut: (VideoCut) -> Unit,
    onEditCaption: (CaptionOverlay) -> Unit,
    onAddCaptionAtPlayhead: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (editPlan == null || editPlan.cuts.isEmpty()) return

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Track Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "🎞️ Interactive Timeline",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "• Tap cut to trim",
                    fontSize = 10.sp,
                    color = TextMuted
                )
            }

            Text(
                text = "${editPlan.cuts.size} cuts • ${String.format("%.1f", totalDurationSec)}s",
                fontSize = 11.sp,
                color = AccentElectricCyan,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Multi-track scrollable container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioCardBg, RoundedCornerShape(12.dp))
                .border(1.dp, StudioCardBgElevated, RoundedCornerShape(12.dp))
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
            ) {
                // Track 1: Video Cuts Track
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(64.dp)
                            .height(46.dp)
                            .background(StudioCardBgElevated, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = null,
                                tint = AccentElectricCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(text = "Clips", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    var accumulatedTime = 0f
                    editPlan.cuts.forEachIndexed { index, cut ->
                        val cutDuration = cut.durationSec
                        val cutWidth = (cutDuration * 38).coerceAtLeast(68f).dp
                        val isActive = cut.id == activeCut?.id
                        val startSec = accumulatedTime

                        Box(
                            modifier = Modifier
                                .width(cutWidth)
                                .height(46.dp)
                                .background(
                                    if (isActive) TrackCutActive else TrackCutBg,
                                    RoundedCornerShape(6.dp)
                                )
                                .border(
                                    width = if (isActive) 1.5.dp else 0.5.dp,
                                    color = if (isActive) AccentElectricCyan else Color.White.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    onSeek(startSec)
                                    onEditCut(cut)
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = cut.clipName,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = "Trim Cut",
                                        tint = if (isActive) Color.White else TextMuted,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                                Text(
                                    text = "${String.format("%.1f", cutDuration)}s • ${cut.playbackSpeed}x ${cut.transitionToNext.icon}",
                                    fontSize = 9.sp,
                                    color = AccentElectricCyan
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))
                        accumulatedTime += cutDuration
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Track 2: Subtitle / Captions Track
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(64.dp)
                            .height(34.dp)
                            .background(StudioCardBgElevated, RoundedCornerShape(6.dp))
                            .clickable { onAddCaptionAtPlayhead() },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Text",
                                tint = AccentNeonPink,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(text = "Text", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    if (editPlan.captions.isEmpty()) {
                        Surface(
                            color = StudioCardBgElevated.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .clickable { onAddCaptionAtPlayhead() }
                                .padding(horizontal = 8.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "+ Tap here or 'Generate with AI' to add captions",
                                    fontSize = 9.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    } else {
                        editPlan.captions.forEach { cap ->
                            val duration = (cap.endTimeSec - cap.startTimeSec).coerceAtLeast(1f)
                            val widthDp = (duration * 38).coerceAtLeast(54f).dp
                            val isActive = cap.id == activeCaption?.id

                            Box(
                                modifier = Modifier
                                    .width(widthDp)
                                    .height(34.dp)
                                    .background(
                                        if (isActive) TrackCaptionActive else TrackCaptionBg,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .border(
                                        width = if (isActive) 1.5.dp else 0.5.dp,
                                        color = if (isActive) AccentNeonPink else Color.White.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        onSeek(cap.startTimeSec)
                                        onEditCaption(cap)
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = "💬 " + cap.text,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))
                        }
                    }
                }
            }
        }
    }
}
