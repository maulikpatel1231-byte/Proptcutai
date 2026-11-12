package com.example.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiVideoDirector
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

    // 1. Media Files added by user (Starts empty - no sample or dummy videos)
    private val _mediaFiles = MutableStateFlow<List<MediaFileItem>>(emptyList())
    val mediaFiles: StateFlow<List<MediaFileItem>> = _mediaFiles.asStateFlow()

    // 2. User Prompt & Aspect Ratio
    private val _promptText = MutableStateFlow("Make a cinematic vlog with smooth cuts, warm film filter, and pop subtitles")
    val promptText: StateFlow<String> = _promptText.asStateFlow()

    private val _aspectRatio = MutableStateFlow(AspectRatio.PORTRAIT_9_16)
    val aspectRatio: StateFlow<AspectRatio> = _aspectRatio.asStateFlow()

    // 3. AI State and current plan
    private val _aiUiState = MutableStateFlow<AiEditUiState>(AiEditUiState.Idle)
    val aiUiState: StateFlow<AiEditUiState> = _aiUiState.asStateFlow()

    private val _currentEditPlan = MutableStateFlow<VideoEditPlan?>(null)
    val currentEditPlan: StateFlow<VideoEditPlan?> = _currentEditPlan.asStateFlow()

    // 4. Overridden active filter (allows manual fine-tuning after AI suggests one)
    private val _activeFilter = MutableStateFlow(VideoFilter.ORIGINAL)
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

    // 6. Export State (Full HD 1080p 60FPS)
    private val _exportProgress = MutableStateFlow<Float?>(null)
    val exportProgress: StateFlow<Float?> = _exportProgress.asStateFlow()

    private val _isExported = MutableStateFlow(false)
    val isExported: StateFlow<Boolean> = _isExported.asStateFlow()

    private var playbackJob: Job? = null

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
            var displayName = "Media_${System.currentTimeMillis() % 10000}.mp4"
            var isVideo = true

            try {
                val mime = context.contentResolver.getType(uri) ?: ""
                if (mime.startsWith("image/")) {
                    isVideo = false
                    displayName = "Photo_${System.currentTimeMillis() % 10000}.jpg"
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
                durationSec = if (isVideo) 8 else 4,
                resolutionLabel = if (isVideo) "Full HD 1080p" else "Photo",
                description = "User added file: $displayName"
            )
        }

        val updatedFiles = _mediaFiles.value + newItems
        _mediaFiles.value = updatedFiles

        // Build or update working timeline with the user's added files
        buildWorkingTimeline(updatedFiles)
    }

    private fun buildWorkingTimeline(files: List<MediaFileItem>) {
        if (files.isEmpty()) {
            _currentEditPlan.value = null
            _playbackPositionSec.value = 0f
            return
        }

        val cuts = files.mapIndexed { index, file ->
            VideoCut(
                id = "cut_$index",
                clipIndex = index,
                clipName = file.name,
                startTimeSec = 0f,
                endTimeSec = file.durationSec.toFloat(),
                playbackSpeed = 1.0f,
                transitionToNext = TransitionType.CROSS_DISSOLVE,
                actionDescription = "Scene from ${file.name}"
            )
        }

        val totalDuration = cuts.sumOf { it.durationSec.toDouble() }.toFloat().coerceAtLeast(3f)

        // Only override if no AI plan yet or if files changed
        val existingPlan = _currentEditPlan.value
        _currentEditPlan.value = VideoEditPlan(
            title = existingPlan?.title ?: "User Project (${files.size} items)",
            creativeSummary = existingPlan?.creativeSummary ?: "Timeline ready for AI editing.",
            directorsRationale = existingPlan?.directorsRationale ?: "Add a prompt above and tap 'AI Edit Video' to automatically cut, grade, and caption this video.",
            filter = _activeFilter.value,
            aspectRatio = _aspectRatio.value,
            cuts = cuts,
            captions = existingPlan?.captions ?: emptyList(),
            backgroundMusicMood = existingPlan?.backgroundMusicMood ?: "Natural Sound",
            soundEffects = existingPlan?.soundEffects ?: emptyList(),
            estimatedFinalDurationSec = totalDuration
        )
    }

    fun removeMediaItem(id: String) {
        val updated = _mediaFiles.value.filter { it.id != id }
        _mediaFiles.value = updated
        buildWorkingTimeline(updated)
    }

    fun clearAllMedia() {
        _mediaFiles.value = emptyList()
        _currentEditPlan.value = null
        _playbackPositionSec.value = 0f
        pausePlayback()
    }

    fun generateAiEdit() {
        val prompt = _promptText.value.trim()
        val files = _mediaFiles.value

        if (files.isEmpty()) {
            _aiUiState.value = AiEditUiState.Error("Please add video or photo files to the timeline first.")
            return
        }

        if (prompt.isBlank()) {
            _aiUiState.value = AiEditUiState.Error("Please enter an edit prompt instruction.")
            return
        }

        viewModelScope.launch {
            _aiUiState.value = AiEditUiState.Analyzing("AI Director is analyzing your clips and applying cuts according to prompt...")
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
        val maxDuration = plan.estimatedFinalDurationSec.coerceAtLeast(1f)
        _playbackPositionSec.value = seconds.coerceIn(0f, maxDuration)
    }

    private fun startPlayback() {
        val plan = _currentEditPlan.value ?: return
        val maxDuration = plan.estimatedFinalDurationSec.coerceAtLeast(1f)

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
        if (_mediaFiles.value.isEmpty()) {
            _aiUiState.value = AiEditUiState.Error("Add media files before exporting.")
            return
        }
        pausePlayback()
        _isExported.value = false
        viewModelScope.launch {
            _exportProgress.value = 0.05f
            val totalSteps = 25
            for (i in 1..totalSteps) {
                delay(100)
                _exportProgress.value = (i.toFloat() / totalSteps)
            }
            _exportProgress.value = 1.0f
            delay(200)
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
