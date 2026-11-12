package com.example.model

import android.net.Uri

enum class MediaType {
    VIDEO,
    IMAGE
}

data class MediaFileItem(
    val id: String,
    val name: String,
    val uri: Uri,
    val type: MediaType = MediaType.VIDEO,
    val durationSec: Int = 10,
    val resolutionLabel: String = "1080p",
    val description: String = ""
)

enum class AspectRatio(val label: String, val ratioWidth: Float, val ratioHeight: Float, val iconLabel: String) {
    PORTRAIT_9_16("9:16 (Reels/Shorts)", 9f, 16f, "9:16"),
    LANDSCAPE_16_9("16:9 (YouTube)", 16f, 9f, "16:9"),
    SQUARE_1_1("1:1 (Post)", 1f, 1f, "1:1")
}

enum class VideoFilter(
    val displayName: String,
    val description: String,
    val saturation: Float = 1.0f,
    val contrast: Float = 1.0f,
    val warmthHue: Float = 0.0f,
    val brightness: Float = 0.0f
) {
    ORIGINAL("Original", "Natural camera footage", 1.0f, 1.0f, 0f, 0f),
    CINEMATIC_WARM("Cinematic Warm", "Warm golden film tone with deep shadows", 1.25f, 1.18f, 0.25f, 0.02f),
    RETRO_VHS("Retro VHS 90s", "Nostalgic color bleed and analog CRT feel", 1.15f, 0.95f, -0.1f, 0.05f),
    NEON_CYBER("Cyberpunk Neon", "High vibrance with cool cyan & hot magenta", 1.6f, 1.3f, -0.2f, 0.04f),
    NOIR_MONOCHROME("Noir B&W", "Classic dramatic black and white high contrast", 0.0f, 1.4f, 0f, -0.05f),
    VIVID_POP("Vivid Pop", "Hyper-punchy saturated colors for social media", 1.55f, 1.25f, 0.05f, 0.03f),
    PASTEL_DREAM("Pastel Dream", "Soft, dreamy low-contrast aesthetic", 0.85f, 0.9f, 0.15f, 0.08f)
}

enum class TransitionType(val displayName: String, val icon: String) {
    CUT("Hard Cut", "✂️"),
    CROSS_DISSOLVE("Cross Dissolve", "✨"),
    ZOOM_IN("Zoom In Punch", "🔍"),
    SLIDE_LEFT("Slide Left", "◀️"),
    GLITCH_TRANSITION("Glitch Flash", "⚡"),
    FADE_BLACK("Fade to Black", "🌑")
}

enum class CaptionPosition {
    TOP,
    CENTER,
    BOTTOM
}

enum class CaptionStyle(val displayName: String, val textColor: String, val highlightColor: String) {
    TIKTOK_YELLOW("Reels Yellow", "#FFFFFF", "#FFDE59"),
    NEON_CYAN("Cyber Cyan", "#E0FFFF", "#00F0FF"),
    CLASSIC_WHITE("Minimal White", "#FFFFFF", "#F3F4F6"),
    BOXED_BLACK("Boxed Bold", "#FFFFFF", "#FF4757")
}

data class CaptionOverlay(
    val id: String,
    val text: String,
    val startTimeSec: Float,
    val endTimeSec: Float,
    val position: CaptionPosition = CaptionPosition.BOTTOM,
    val style: CaptionStyle = CaptionStyle.TIKTOK_YELLOW,
    val textColorHex: String = "#FFFFFF",
    val highlightColorHex: String = "#FFDE59"
)

data class VideoCut(
    val id: String,
    val clipIndex: Int,
    val clipName: String,
    val startTimeSec: Float,
    val endTimeSec: Float,
    val playbackSpeed: Float = 1.0f,
    val transitionToNext: TransitionType = TransitionType.CROSS_DISSOLVE,
    val actionDescription: String = ""
) {
    val durationSec: Float get() = (endTimeSec - startTimeSec).coerceAtLeast(0.5f) / playbackSpeed
}

enum class ExportQuality(val displayName: String, val resolution: String, val bitrate: String) {
    FULL_HD_1080P("Full HD 1080p", "1080x1920", "16 Mbps"),
    ULTRA_HD_4K("4K Ultra HD", "2160x3840", "45 Mbps"),
    HD_720P("HD 720p (Fast)", "720x1280", "8 Mbps")
}

data class VideoEditPlan(
    val title: String,
    val creativeSummary: String,
    val directorsRationale: String,
    val filter: VideoFilter = VideoFilter.CINEMATIC_WARM,
    val filterIntensity: Float = 1.0f,
    val aspectRatio: AspectRatio = AspectRatio.PORTRAIT_9_16,
    val cuts: List<VideoCut> = emptyList(),
    val captions: List<CaptionOverlay> = emptyList(),
    val backgroundMusicMood: String = "Lofi Upbeat Groove",
    val soundEffects: List<String> = emptyList(),
    val estimatedFinalDurationSec: Float = 15f,
    val isAudioMuted: Boolean = false,
    val audioVolume: Float = 0.85f
)

sealed interface AiEditUiState {
    data object Idle : AiEditUiState
    data class Analyzing(val stepMessage: String) : AiEditUiState
    data class Success(val plan: VideoEditPlan) : AiEditUiState
    data class Error(val message: String) : AiEditUiState
}
