package com.example.data

import android.net.Uri
import com.example.model.MediaFileItem
import com.example.model.MediaType

object SampleMediaProvider {
    val sampleVideos = listOf(
        MediaFileItem(
            id = "sample_beach_drone",
            name = "Golden_Coast_Drone.mp4",
            uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"),
            type = MediaType.VIDEO,
            durationSec = 15,
            isSample = true,
            sampleVideoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
            resolutionLabel = "4K 60fps",
            description = "High-altitude cinematic drone shot tracking golden beach waves at sunset."
        ),
        MediaFileItem(
            id = "sample_city_lights",
            name = "Neon_Tokyo_Night.mp4",
            uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4"),
            type = MediaType.VIDEO,
            durationSec = 15,
            isSample = true,
            sampleVideoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
            resolutionLabel = "1080p",
            description = "Vibrant night street scene with reflections, neon signboards, and urban traffic."
        ),
        MediaFileItem(
            id = "sample_action_skate",
            name = "Skate_Park_Action.mp4",
            uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4"),
            type = MediaType.VIDEO,
            durationSec = 12,
            isSample = true,
            sampleVideoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
            resolutionLabel = "1080p 120fps",
            description = "Fast dynamic wide shot of skateboarder launching into a halfpipe air grab."
        ),
        MediaFileItem(
            id = "sample_coffee_macro",
            name = "Espresso_Pour_Macro.mp4",
            uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyBlazes.mp4"),
            type = MediaType.VIDEO,
            durationSec = 15,
            isSample = true,
            sampleVideoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyBlazes.mp4",
            resolutionLabel = "4K 30fps",
            description = "Crisp slow-motion macro shot of espresso pouring with rich crema into glass."
        )
    )

    val promptSuggestions = listOf(
        "⚡ Viral Reel: High energy pacing with bold pop captions, zoom cuts, and vibrant summer grading",
        "🎬 Cinematic Trailer: Moody 24fps film aesthetic, slow zoom transitions, letterbox, and dramatic captions",
        "📼 90s Retro VHS: Nostalgic analog tape filter, timestamp overlay, glitch transitions, and lo-fi vibe",
        "✈️ Travel Vlog: Warm golden hour glow, smooth cross-fades, punchy text callouts, and relaxed vibe",
        "🎵 Beat Drop Montage: Rapid-fire cuts synced to tempo, 1.5x speed ramps, and neon cyber glow"
    )
}
