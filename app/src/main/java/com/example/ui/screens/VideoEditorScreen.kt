package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.ExportDialog
import com.example.ui.components.MediaPickerSection
import com.example.ui.components.PromptEditorSection
import com.example.ui.components.TimelineTracksView
import com.example.ui.components.VideoPlayerCanvas
import com.example.ui.theme.AccentElectricCyan
import com.example.ui.theme.PrimaryNeonPurple
import com.example.ui.theme.StudioCardBg
import com.example.ui.theme.StudioCardBgElevated
import com.example.ui.theme.StudioDarkBg
import com.example.viewmodel.VideoEditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorScreen(
    viewModel: VideoEditorViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mediaFiles by viewModel.mediaFiles.collectAsStateWithLifecycle()
    val promptText by viewModel.promptText.collectAsStateWithLifecycle()
    val aspectRatio by viewModel.aspectRatio.collectAsStateWithLifecycle()
    val aiUiState by viewModel.aiUiState.collectAsStateWithLifecycle()
    val currentEditPlan by viewModel.currentEditPlan.collectAsStateWithLifecycle()
    val activeFilter by viewModel.activeFilter.collectAsStateWithLifecycle()
    val playbackPositionSec by viewModel.playbackPositionSec.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val activeCut by viewModel.activeCut.collectAsStateWithLifecycle()
    val activeCaption by viewModel.activeCaption.collectAsStateWithLifecycle()
    val exportProgress by viewModel.exportProgress.collectAsStateWithLifecycle()
    val isExported by viewModel.isExported.collectAsStateWithLifecycle()

    val totalDuration = currentEditPlan?.estimatedFinalDurationSec ?: 12f

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    Brush.linearGradient(listOf(PrimaryNeonPurple, AccentElectricCyan)),
                                    RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MovieCreation,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "PromptCut AI",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Prompt to Video Studio",
                                fontSize = 11.sp,
                                color = AccentElectricCyan
                            )
                        }
                    }
                },
                actions = {
                    Button(
                        onClick = { viewModel.startExport() },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentElectricCyan,
                            contentColor = Color(0xFF0F172A)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("export_video_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Export Video",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Export",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = StudioDarkBg,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = StudioDarkBg,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp)
            ) {
                // 1. Video Player Canvas with live playback, color matrix, aspect ratio & subtitle overlays
                VideoPlayerCanvas(
                    aspectRatio = aspectRatio,
                    activeFilter = activeFilter,
                    activeCut = activeCut,
                    activeCaption = activeCaption,
                    mediaFiles = mediaFiles,
                    isPlaying = isPlaying,
                    playbackPositionSec = playbackPositionSec,
                    totalDurationSec = totalDuration,
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onSeek = { viewModel.seekTo(it) }
                )

                // 2. Multi-track visual timeline
                TimelineTracksView(
                    editPlan = currentEditPlan,
                    playbackPositionSec = playbackPositionSec,
                    totalDurationSec = totalDuration,
                    activeCut = activeCut,
                    activeCaption = activeCaption,
                    onSeek = { viewModel.seekTo(it) }
                )

                // 3. Media Files Manager (Add files option, list clips, reset samples)
                MediaPickerSection(
                    mediaFiles = mediaFiles,
                    activeClipIndex = activeCut?.clipIndex,
                    onAddFiles = { uris -> viewModel.addMediaUris(context, uris) },
                    onRemoveFile = { id -> viewModel.removeMediaItem(id) },
                    onLoadSamplePack = { viewModel.resetToSampleFiles() },
                    onSelectClip = { index ->
                        // Jump playback to start of this clip
                        val plan = currentEditPlan
                        if (plan != null) {
                            var accum = 0f
                            for (cut in plan.cuts) {
                                if (cut.clipIndex == index) {
                                    viewModel.seekTo(accum)
                                    break
                                }
                                accum += cut.durationSec
                            }
                        }
                    }
                )

                // 4. Prompt Input, Presets, Filter Tweaks, and AI Edit Trigger
                PromptEditorSection(
                    promptText = promptText,
                    aspectRatio = aspectRatio,
                    activeFilter = activeFilter,
                    aiUiState = aiUiState,
                    currentEditPlan = currentEditPlan,
                    onPromptChange = { viewModel.setPrompt(it) },
                    onAspectRatioChange = { viewModel.setAspectRatio(it) },
                    onFilterChange = { viewModel.setFilter(it) },
                    onGenerateEdit = { viewModel.generateAiEdit() }
                )
            }

            // Export Simulation Modal Dialog
            ExportDialog(
                exportProgress = exportProgress,
                isExported = isExported,
                editPlan = currentEditPlan,
                onDismiss = { viewModel.dismissExport() }
            )
        }
    }
}
