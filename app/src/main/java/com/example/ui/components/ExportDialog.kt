package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.export.ExportResult
import com.example.model.ExportQuality
import com.example.model.VideoEditPlan
import com.example.ui.theme.AccentElectricCyan
import com.example.ui.theme.PrimaryNeonPurple
import com.example.ui.theme.StudioCardBg
import com.example.ui.theme.StudioCardBgElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun ExportDialog(
    exportProgress: Float?,
    exportStage: String,
    isExported: Boolean,
    exportResult: ExportResult?,
    selectedQuality: ExportQuality,
    editPlan: VideoEditPlan?,
    onPreview: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    if (exportProgress == null && !isExported) return

    Dialog(onDismissRequest = {
        if (isExported) onDismiss()
    }) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = StudioCardBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, StudioCardBgElevated),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!isExported) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(PrimaryNeonPurple.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = PrimaryNeonPurple,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Rendering in ${selectedQuality.displayName}...",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = exportStage,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    LinearProgressIndicator(
                        progress = { exportProgress ?: 0f },
                        color = AccentElectricCyan,
                        trackColor = StudioCardBgElevated,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${((exportProgress ?: 0f) * 100).toInt()}% Encoded",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(Color(0x2210B981), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Full HD Video Exported!",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = exportResult?.fileName ?: editPlan?.title ?: "PromptCut_Master.mp4",
                        fontSize = 12.sp,
                        color = AccentElectricCyan,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (exportResult?.galleryUri != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Saved to Movies/PromptCut in Gallery",
                            fontSize = 11.sp,
                            color = Color(0xFF34D399)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = StudioCardBgElevated)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            SpecRow("Resolution", exportResult?.resolutionLabel ?: "1080 x 1920 (Full HD)")
                            Spacer(modifier = Modifier.height(4.dp))
                            SpecRow("Duration", String.format(Locale.US, "%.1f sec", exportResult?.durationSeconds ?: 0f))
                            Spacer(modifier = Modifier.height(4.dp))
                            SpecRow("File Size", exportResult?.formattedSize ?: "--")
                            Spacer(modifier = Modifier.height(4.dp))
                            SpecRow("Codec", "H.264 / MP4")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onPreview() },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Play Video", fontSize = 12.sp, color = Color.White)
                        }

                        Button(
                            onClick = {
                                onShare()
                                onDismiss()
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryNeonPurple),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Share Video", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    TextButton(onClick = onDismiss) {
                        Text(text = "Done", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 11.sp, color = TextMuted)
        Text(text = value, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Medium)
    }
}
