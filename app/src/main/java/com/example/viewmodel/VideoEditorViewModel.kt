package com.example.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiVideoDirector
import com.example.data.SampleMediaProvider
import com.example.model.AiEditUiState
import com.example.model.AspectRatio
import com.example.model.CaptionOverlay
import com.example.model.MediaFileItem
import com.example.model.MediaType
import com.example.model.TransitionType
import com.example.model.VideoCut
import com.example.model.VideoEditPlan
import com.example.model.VideoFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "VideoEditorViewModel"

class VideoEditorViewModel : ViewModel() {

    private val aiDirector = AiVideoDirector()

    // 1. Media Files added by user or preloaded samples
    private val _mediaFiles = MutableStateFlow<List<MediaFileItem>>(SampleMediaProvider.sampleVideos)
    val mediaFiles: StateFlow<List<MediaFileItem>> = _mediaFiles.asStateFlow()

    // 2. User Prompt & Aspect Ratio
    private val _promptText = MutableStateFlow("Make a cinematic reel with high contrast, pop subtitles, and fast jump cuts")
    val promptText: StateFlow<String> = _promptText.asStateFlow()

    private val _aspectRatio = MutableStateFlow(AspectRatio.PORTRAIT_9_16)
    val aspectRatio: StateFlow<AspectRatio> = _aspectRatio.asStateFlow()

    // 3. AI State and current plan
    private val _aiUiState = MutableStateFlow<AiEditUiState>(AiEditUiState.Idle)
    val aiUiState: StateFlow<AiEditUiState> = _aiUiState.asStateFlow()

    private val _currentEditPlan = MutableStateFlow<VideoEditPlan?>(null)
    val currentEditPlan: StateFlow<VideoEditPlan?> = _currentEditPlan.asStateFlow()

    // 4. Overridden active filter (allows manual fine-tuning after AI suggests one)
    private val _activeFilter = MutableStateFlow(VideoFilter.CINEMATIC_WARM)
    val activeFilter: StateFlow<VideoFilter> = _activeFilter.asStateFlow()

    // 5. Playback & Timeline State
    private val _playbackPositionSec = MutableStateFlow(0f)
    val playbackPositionSec: StateFlow<Float> = _playbackPositionSec.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    // Active cut and caption derived from playback position
    val activeCut: StateFlow<VideoCut?> = combine(_playbackPositionSec, _currentEditPlan) { pos, plan ->
        if (plan == null || plan.cuts.isEmpty()) return@combine null
        var accumulated = 0f
        for (cut in plan.cuts) {
            val cutDuration = cut.durationSec
            if (pos >= accumulated && pos < accumulated + cutDuration) {
                return@combine cut
            }
            accumulated += cutDuration
        }
        plan.cuts.lastOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeCaption: StateFlow<CaptionOverlay?> = combine(_playbackPositionSec, _currentEditPlan) { pos, plan ->
        plan?.captions?.firstOrNull { pos >= it.startTimeSec && pos <= it.endTimeSec }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // 6. Export State
    private val _exportProgress = MutableStateFlow<Float?>(null)
    val exportProgress: StateFlow<Float?> = _exportProgress.asStateFlow()

    private val _isExported = MutableStateFlow(false)
    val isExported: StateFlow<Boolean> = _isExported.asStateFlow()

    private var playbackJob: Job? = null

    init {
        // Generate an initial edit plan with default samples so user sees a ready-to-play video right away
        generateInitialDefaultPlan()
    }

    private fun generateInitialDefaultPlan() {
        val initialPlan = VideoEditPlan(
            title = "Cinematic Coast & Neon Lights",
            creativeSummary = "Paced montage contrasting serene golden coastal sunset with electrifying Tokyo neon vibes.",
            directorsRationale = "Cut 1 starts wide on the drone ocean shot to establish scale. Jump cut to neon Tokyo street reflections creates vibrant juxtaposition. High-contrast Cinematic Warm grade binds the lighting together.",
            filter = VideoFilter.CINEMATIC_WARM,
            aspectRatio = AspectRatio.PORTRAIT_9_16,
            cuts = listOf(
                VideoCut(
                    id = "init_0",
                    clipIndex = 0,
                    clipName = SampleMediaProvider.sampleVideos[0].name,
                    startTimeSec = 0f,
                    endTimeSec = 4.5f,
                    playbackSpeed = 1.0f,
                    transitionToNext = TransitionType.ZOOM_IN,
                    actionDescription = "Golden ocean horizon establishing shot"
                ),
                VideoCut(
                    id = "init_1",
                    clipIndex = 1,
                    clipName = SampleMediaProvider.sampleVideos[1].name,
                    startTimeSec = 2f,
                    endTimeSec = 6.5f,
                    playbackSpeed = 1.2f,
                    transitionToNext = TransitionType.GLITCH_TRANSITION,
                    actionDescription = "Cyberpunk neon street traffic reflections"
                ),
                VideoCut(
                    id = "init_2",
                    clipIndex = 2,
                    clipName = SampleMediaProvider.sampleVideos[2].name,
                    startTimeSec = 1f,
                    endTimeSec = 5f,
                    playbackSpeed = 1.5f,
                    transitionToNext = TransitionType.FADE_BLACK,
                    actionDescription = "High speed halfpipe jump climax"
                )
            ),
            captions = listOf(
                CaptionOverlay(
                    id = "c_0",
                    text = "GOLDEN HORIZON ✨",
                    startTimeSec = 0.5f,
                    endTimeSec = 3.5f,
                    highlightColorHex = "#FFD700"
                ),
                CaptionOverlay(
                    id = "c_1",
                    text = "NEON DREAMS IN MOTION 🔥",
                    startTimeSec = 4.6f,
                    endTimeSec = 8.0f,
                    highlightColorHex = "#00FFFF"
                ),
                CaptionOverlay(
                    id = "c_2",
                    text = "PURE ADRENALINE ⚡",
                    startTimeSec = 8.5f,
                    endTimeSec = 11.5f,
                    highlightColorHex = "#FF3366"
                )
            ),
            backgroundMusicMood = "Synthwave Pulse 128 BPM",
            soundEffects = listOf("Whoosh Transition", "Bass Drop", "Vinyl Tape Stop"),
            estimatedFinalDurationSec = 12f
        )
        _currentEditPlan.value = initialPlan
        _activeFilter.value = initialPlan.filter
    }

    fun setPrompt(text: String) {
        _promptText.value = text
    }

    fun setAspectRatio(ratio: AspectRatio) {
        _aspectRatio.value = ratio
    }

    fun setFilter(filter: VideoFilter) {
        _activeFilter.value = filter
    }

    fun addMediaUris(context: Context, uris: List<Uri>) {
        val newItems = uris.map { uri ->
            var displayName = "User_Clip_${System.currentTimeMillis() % 10000}.mp4"
            var isVideo = true

            try {
                val mime = context.contentResolver.getType(uri) ?: ""
                if (mime.startsWith("image/")) {
                    isVideo = false
                    displayName = "User_Photo_${System.currentTimeMillis() % 10000}.jpg"
                }

                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex != -1) {
                        displayName = cursor.getString(nameIndex) ?: displayName
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not resolve URI details", e)
            }

            MediaFileItem(
                id = UUID.randomUUID().toString(),
                name = displayName,
                uri = uri,
                type = if (isVideo) MediaType.VIDEO else MediaType.IMAGE,
                durationSec = if (isVideo) 10 else 4,
                isSample = false,
                resolutionLabel = if (isVideo) "HD Clip" else "Photo",
                description = "User added file: $displayName"
            )
        }

        _mediaFiles.value = _mediaFiles.value + newItems
    }

    fun removeMediaItem(id: String) {
        _mediaFiles.value = _mediaFiles.value.filter { it.id != id }
    }

    fun resetToSampleFiles() {
        _mediaFiles.value = SampleMediaProvider.sampleVideos
    }

    fun generateAiEdit() {
        val prompt = _promptText.value.trim()
        val files = _mediaFiles.value

        if (files.isEmpty()) {
            _aiUiState.value = AiEditUiState.Error("Please add at least one video or photo first.")
            return
        }

        if (prompt.isBlank()) {
            _aiUiState.value = AiEditUiState.Error("Please enter an edit prompt instruction.")
            return
        }

        viewModelScope.launch {
            _aiUiState.value = AiEditUiState.Analyzing("Analyzing clips and edit prompt with Gemini 3.8 Flash...")
            pausePlayback()

            try {
                val plan = aiDirector.generateEditPlan(
                    prompt = prompt,
                    mediaFiles = files,
                    aspectRatio = _aspectRatio.value
                )
                _currentEditPlan.value = plan
                _activeFilter.value = plan.filter
                _playbackPositionSec.value = 0f
                _aiUiState.value = AiEditUiState.Success(plan)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error generating AI edit", e)
                _aiUiState.value = AiEditUiState.Error(
                    e.localizedMessage ?: "Failed to generate video edit from prompt."
                )
            }
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    fun seekTo(seconds: Float) {
        val plan = _currentEditPlan.value ?: return
        val maxDuration = plan.estimatedFinalDurationSec.coerceAtLeast(3f)
        _playbackPositionSec.value = seconds.coerceIn(0f, maxDuration)
    }

    private fun startPlayback() {
        val plan = _currentEditPlan.value ?: return
        val maxDuration = plan.estimatedFinalDurationSec.coerceAtLeast(3f)

        if (_playbackPositionSec.value >= maxDuration) {
            _playbackPositionSec.value = 0f
        }

        _isPlaying.value = true
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            val stepMs = 50L
            while (_isPlaying.value) {
                delay(stepMs)
                val newPos = _playbackPositionSec.value + (stepMs / 1000f)
                if (newPos >= maxDuration) {
                    _playbackPositionSec.value = 0f
                } else {
                    _playbackPositionSec.value = newPos
                }
            }
        }
    }

    fun pausePlayback() {
        _isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
    }

    fun startExport() {
        pausePlayback()
        _isExported.value = false
        viewModelScope.launch {
            _exportProgress.value = 0.05f
            val totalSteps = 20
            for (i in 1..totalSteps) {
                delay(120)
                _exportProgress.value = (i.toFloat() / totalSteps)
            }
            _exportProgress.value = 1.0f
            delay(300)
            _isExported.value = true
        }
    }

    fun dismissExport() {
        _exportProgress.value = null
        _isExported.value = false
    }

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
    }
}
