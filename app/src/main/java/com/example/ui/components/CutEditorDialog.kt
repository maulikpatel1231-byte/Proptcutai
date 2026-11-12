package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.TransitionType
import com.example.model.VideoCut
import com.example.ui.theme.AccentElectricCyan
import com.example.ui.theme.AccentRed
import com.example.ui.theme.PrimaryNeonPurple
import com.example.ui.theme.StudioCardBg
import com.example.ui.theme.StudioCardBgElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun CutEditorDialog(
    cut: VideoCut,
    maxClipDuration: Float,
    onSaveCut: (VideoCut) -> Unit,
    onDeleteCut: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var startTrim by remember { mutableFloatStateOf(cut.startTimeSec) }
    var endTrim by remember { mutableFloatStateOf(cut.endTimeSec.coerceAtLeast(startTrim + 0.5f)) }
    var speed by remember { mutableFloatStateOf(cut.playbackSpeed) }
    var transition by remember { mutableStateOf(cut.transitionToNext) }

    val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    val totalCutDuration = (endTrim - startTrim).coerceAtLeast(0.5f) / speed

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = StudioCardBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, StudioCardBgElevated),
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(PrimaryNeonPurple.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCut,
                                contentDescription = null,
                                tint = PrimaryNeonPurple,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Trim & Cut Editor",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = cut.clipName,
                                fontSize = 11.sp,
                                color = AccentElectricCyan
                            )
                        }
                    }

                    IconButton(onClick = { onDeleteCut(cut.id); onDismiss() }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Cut",
                            tint = AccentRed.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Cut Duration Summary Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = StudioCardBgElevated),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Final Duration:", fontSize = 12.sp, color = TextSecondary)
                        Text(
                            text = String.format(Locale.US, "%.1f sec (at %.2fx)", totalCutDuration, speed),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentElectricCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Trimming Sliders
                Text(
                    text = "Trim Range: ${String.format(Locale.US, "%.1f", startTrim)}s to ${String.format(Locale.US, "%.1f", endTrim)}s",
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(text = "Start Trim Point:", fontSize = 11.sp, color = TextMuted)
                Slider(
                    value = startTrim,
                    onValueChange = {
                        startTrim = it.coerceAtMost(endTrim - 0.5f)
                    },
                    valueRange = 0f..maxClipDuration.coerceAtLeast(4f),
                    colors = SliderDefaults.colors(thumbColor = PrimaryNeonPurple, activeTrackColor = PrimaryNeonPurple)
                )

                Text(text = "End Trim Point:", fontSize = 11.sp, color = TextMuted)
                Slider(
                    value = endTrim,
                    onValueChange = {
                        endTrim = it.coerceAtLeast(startTrim + 0.5f)
                    },
                    valueRange = 0f..maxClipDuration.coerceAtLeast(4f),
                    colors = SliderDefaults.colors(thumbColor = AccentElectricCyan, activeTrackColor = AccentElectricCyan)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Playback Speed Selector
                Text(
                    text = "Playback Speed Ramping:",
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    speedOptions.forEach { s ->
                        val isSel = speed == s
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .background(
                                    if (isSel) PrimaryNeonPurple else StudioCardBgElevated,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { speed = s },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${s}x",
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Transition to Next Scene
                Text(
                    text = "Transition to Next Cut:",
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TransitionType.entries.take(4).forEach { trans ->
                        val isSel = transition == trans
                        Surface(
                            color = if (isSel) AccentElectricCyan.copy(alpha = 0.2f) else StudioCardBgElevated,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSel) AccentElectricCyan else Color.Transparent
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { transition = trans }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(text = trans.icon, fontSize = 14.sp)
                                Text(
                                    text = trans.displayName.take(7),
                                    fontSize = 9.sp,
                                    color = if (isSel) AccentElectricCyan else TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Actions: Cancel & Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(text = "Cancel", fontSize = 12.sp, color = TextSecondary)
                    }

                    Button(
                        onClick = {
                            val updated = cut.copy(
                                startTimeSec = startTrim,
                                endTimeSec = endTrim,
                                playbackSpeed = speed,
                                transitionToNext = transition
                            )
                            onSaveCut(updated)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryNeonPurple),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Done, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Apply Cut", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
