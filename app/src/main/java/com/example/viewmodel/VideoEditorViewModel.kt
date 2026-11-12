package com.example.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiVideoDirector
import com.example.audio.StudioSoundEngine
import com.example.export.ExportResult
import com.example.export.VideoExportEngine
import com.example.model.AiEditUiState
import com.example.model.AspectRatio
import com.example.model.CaptionOverlay
import com.example.model.CaptionPosition
import com.example.model.CaptionStyle
import com.example.model.ExportQuality
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
    private val soundEngine = StudioSoundEngine()
    private val exportEngine = VideoExportEngine()

    // 1. Media Files added by user
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

    // 4. Overridden active filter & intensity
    private val _activeFilter = MutableStateFlow(VideoFilter.ORIGINAL)
    val activeFilter: StateFlow<VideoFilter> = _activeFilter.asStateFlow()

    private val _filterIntensity = MutableStateFlow(1.0f)
    val filterIntensity: StateFlow<Float> = _filterIntensity.asStateFlow()

    // 5. Audio controls
    private val _isAudioMuted = MutableStateFlow(false)
    val isAudioMuted: StateFlow<Boolean> = _isAudioMuted.asStateFlow()

    // 6. Playback & Timeline State
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

    // 7. Modals: Cut Inspector & Caption Inspector
    private val _editingCut = MutableStateFlow<VideoCut?>(null)
    val editingCut: StateFlow<VideoCut?> = _editingCut.asStateFlow()

    private val _editingCaption = MutableStateFlow<CaptionOverlay?>(null)
    val editingCaption: StateFlow<CaptionOverlay?> = _editingCaption.asStateFlow()

    // 8. Export State
    private val _exportQuality = MutableStateFlow(ExportQuality.FULL_HD_1080P)
    val exportQuality: StateFlow<ExportQuality> = _exportQuality.asStateFlow()

    private val _exportProgress = MutableStateFlow<Float?>(null)
    val exportProgress: StateFlow<Float?> = _exportProgress.asStateFlow()

    private val _isExported = MutableStateFlow(false)
    val isExported: StateFlow<Boolean> = _isExported.asStateFlow()

    private val _exportResult = MutableStateFlow<ExportResult?>(null)
    val exportResult: StateFlow<ExportResult?> = _exportResult.asStateFlow()

    private var playbackJob: Job? = null
    private var lastCutIdSeen: String? = null

    fun setPrompt(text: String) {
        _promptText.value = text
    }

    fun setAspectRatio(ratio: AspectRatio) {
        _aspectRatio.value = ratio
    }

    fun setFilter(filter: VideoFilter) {
        _activeFilter.value = filter
    }

    fun setFilterIntensity(intensity: Float) {
        _filterIntensity.value = intensity.coerceIn(0.1f, 1.0f)
    }

    fun toggleAudioMute() {
        val newMute = !_isAudioMuted.value
        _isAudioMuted.value = newMute
        soundEngine.setMuted(newMute)
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
        val existingPlan = _currentEditPlan.value

        _currentEditPlan.value = VideoEditPlan(
            title = existingPlan?.title ?: "User Project (${files.size} items)",
            creativeSummary = existingPlan?.creativeSummary ?: "Timeline populated with ${files.size} clips. Ready for AI prompt editing.",
            directorsRationale = existingPlan?.directorsRationale ?: "Add a prompt above and tap 'Generate Video Edit with AI' to let Gemini craft cinematic cuts and captions.",
            filter = _activeFilter.value,
            filterIntensity = _filterIntensity.value,
            aspectRatio = _aspectRatio.value,
            cuts = cuts,
            captions = existingPlan?.captions ?: emptyList(),
            backgroundMusicMood = existingPlan?.backgroundMusicMood ?: "Chill Lofi Synth",
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
            _aiUiState.value = AiEditUiState.Analyzing("AI Director is analyzing your clips with Gemini 3.8 Flash...")
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
        soundEngine.startAmbientTrack(viewModelScope, plan.backgroundMusicMood)

        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            val stepMs = 50L
            while (_isPlaying.value) {
                delay(stepMs)
                val newPos = _playbackPositionSec.value + (stepMs / 1000f)

                // Sound chime on scene change
                val currentCut = activeCut.value
                if (currentCut != null && currentCut.id != lastCutIdSeen) {
                    if (lastCutIdSeen != null) {
                        soundEngine.playCutChime(viewModelScope)
                    }
                    lastCutIdSeen = currentCut.id
                }

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
        soundEngine.stopAmbientTrack()
    }

    // Cut & Timeline Manipulation Tools
    fun splitAtPlayhead() {
        val plan = _currentEditPlan.value ?: return
        val pos = _playbackPositionSec.value

        var accum = 0f
        val cuts = plan.cuts.toMutableList()
        for (i in cuts.indices) {
            val cut = cuts[i]
            val duration = cut.durationSec
            if (pos > accum + 0.3f && pos < accum + duration - 0.3f) {
                // Split point inside cut
                val relativeSplit = (pos - accum) * cut.playbackSpeed
                val firstCut = cut.copy(
                    id = UUID.randomUUID().toString(),
                    endTimeSec = cut.startTimeSec + relativeSplit
                )
                val secondCut = cut.copy(
                    id = UUID.randomUUID().toString(),
                    startTimeSec = cut.startTimeSec + relativeSplit
                )
                cuts.removeAt(i)
                cuts.add(i, secondCut)
                cuts.add(i, firstCut)

                _currentEditPlan.value = plan.copy(cuts = cuts)
                soundEngine.playCutChime(viewModelScope)
                return
            }
            accum += duration
        }
    }

    fun openCutEditor(cut: VideoCut) {
        _editingCut.value = cut
    }

    fun closeCutEditor() {
        _editingCut.value = null
    }

    fun updateCut(updatedCut: VideoCut) {
        val plan = _currentEditPlan.value ?: return
        val newCuts = plan.cuts.map { if (it.id == updatedCut.id) updatedCut else it }
        val newTotal = newCuts.sumOf { it.durationSec.toDouble() }.toFloat().coerceAtLeast(1f)
        _currentEditPlan.value = plan.copy(cuts = newCuts, estimatedFinalDurationSec = newTotal)
    }

    fun deleteCut(cutId: String) {
        val plan = _currentEditPlan.value ?: return
        val newCuts = plan.cuts.filter { it.id != cutId }
        val newTotal = newCuts.sumOf { it.durationSec.toDouble() }.toFloat().coerceAtLeast(1f)
        _currentEditPlan.value = plan.copy(cuts = newCuts, estimatedFinalDurationSec = newTotal)
    }

    // Caption Editing
    fun openCaptionEditor(caption: CaptionOverlay) {
        _editingCaption.value = caption
    }

    fun addCaptionAtPlayhead() {
        val pos = _playbackPositionSec.value
        val newCap = CaptionOverlay(
            id = UUID.randomUUID().toString(),
            text = "NEW SUBTITLE",
            startTimeSec = pos,
            endTimeSec = pos + 2.5f,
            position = CaptionPosition.BOTTOM,
            style = CaptionStyle.TIKTOK_YELLOW
        )
        val plan = _currentEditPlan.value ?: return
        _currentEditPlan.value = plan.copy(captions = plan.captions + newCap)
        _editingCaption.value = newCap
    }

    fun closeCaptionEditor() {
        _editingCaption.value = null
    }

    fun updateCaption(updatedCaption: CaptionOverlay) {
        val plan = _currentEditPlan.value ?: return
        val newCaps = plan.captions.map { if (it.id == updatedCaption.id) updatedCaption else it }
        _currentEditPlan.value = plan.copy(captions = newCaps)
    }

    fun deleteCaption(captionId: String) {
        val plan = _currentEditPlan.value ?: return
        val newCaps = plan.captions.filter { it.id != captionId }
        _currentEditPlan.value = plan.copy(captions = newCaps)
    }

    // Export Processing
    fun setExportQuality(quality: ExportQuality) {
        _exportQuality.value = quality
    }

    fun startExport(context: Context) {
        val plan = _currentEditPlan.value ?: return
        if (_mediaFiles.value.isEmpty()) {
            _aiUiState.value = AiEditUiState.Error("Add media files before exporting.")
            return
        }
        pausePlayback()
        _isExported.value = false

        viewModelScope.launch {
            _exportProgress.value = 0.05f
            val totalSteps = 20
            for (i in 1..totalSteps) {
                delay(80)
                _exportProgress.value = (i.toFloat() / totalSteps)
            }

            // Generate real export file
            val result = exportEngine.exportProject(context, plan, _exportQuality.value)
            _exportResult.value = result
            _exportProgress.value = 1.0f
            delay(150)
            _isExported.value = true
        }
    }

    fun shareExportedVideo(context: Context) {
        val result = _exportResult.value ?: return
        exportEngine.shareExportedVideo(context, result)
    }

    fun dismissExport() {
        _exportProgress.value = null
        _isExported.value = false
    }

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
        soundEngine.stopAmbientTrack()
    }
}
