package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import com.example.model.AspectRatio
import com.example.model.CaptionOverlay
import com.example.model.CaptionPosition
import com.example.model.CaptionStyle
import com.example.model.MediaFileItem
import com.example.model.MediaType
import com.example.model.TransitionType
import com.example.model.VideoCut
import com.example.model.VideoEditPlan
import com.example.model.VideoFilter
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "AiVideoDirector"

class AiVideoDirector {

    // Structured JSON schema definition using Schema.obj (required by Firebase AI Logic Kotlin SDK)
    private val cutSchema = Schema.obj(
        properties = mapOf(
            "clipIndex" to Schema.integer("Zero-based index of media file"),
            "clipName" to Schema.string("Name or title of the clip"),
            "startTimeSec" to Schema.double("Start trim position in seconds within clip duration"),
            "endTimeSec" to Schema.double("End trim position in seconds within clip duration"),
            "playbackSpeed" to Schema.double("Speed multiplier between 0.5 and 2.0, e.g. 0.75, 1.0, 1.25, 1.5, 2.0"),
            "transition" to Schema.enumeration(
                values = TransitionType.entries.map { it.name },
                description = "Transition to next clip"
            ),
            "actionDescription" to Schema.string("Brief description of what happens visually in this cut")
        )
    )

    private val captionSchema = Schema.obj(
        properties = mapOf(
            "text" to Schema.string("Short punchy caption or subtitle text (2-7 words) to display on screen"),
            "startTimeSec" to Schema.double("Timestamp in final edited timeline seconds when caption appears"),
            "endTimeSec" to Schema.double("Timestamp in final edited timeline seconds when caption disappears"),
            "position" to Schema.enumeration(
                values = CaptionPosition.entries.map { it.name },
                description = "Screen position for the caption"
            ),
            "textColorHex" to Schema.string("Hex color code for text, e.g. #FFFFFF"),
            "highlightColorHex" to Schema.string("Hex accent highlight color, e.g. #FFDE59 or #00F0FF")
        )
    )

    private val editPlanSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string("Catchy creative project title"),
            "creativeSummary" to Schema.string("Summary of video edit style, scene flow, and pacing"),
            "directorsRationale" to Schema.string("Specific explanation referencing the visual content of the clips and why these cuts, filter, and captions match the prompt"),
            "filterName" to Schema.enumeration(
                values = VideoFilter.entries.map { it.name },
                description = "Visual color grade filter"
            ),
            "backgroundMusicMood" to Schema.string("Suggested background audio mood or track genre"),
            "cuts" to Schema.array(cutSchema, "List of sequence cuts for the edited video timeline"),
            "captions" to Schema.array(captionSchema, "List of synchronized caption overlays across the edited timeline"),
            "soundEffects" to Schema.array(Schema.string(), "Suggested sound effects")
        )
    )

    private val config = generationConfig {
        responseMimeType = "application/json"
        responseSchema = editPlanSchema
    }

    // Lazy initialization of GenerativeModel as mandated by Firebase AI Logic guidelines
    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = config
        )
    }

    suspend fun generateEditPlan(
        context: Context,
        prompt: String,
        mediaFiles: List<MediaFileItem>,
        aspectRatio: AspectRatio,
        targetDurationSec: Int = 15
    ): VideoEditPlan {
        if (mediaFiles.isEmpty()) {
            throw IllegalArgumentException("At least one video or photo file must be provided.")
        }

        // Extract lightweight representative frames from user's actual videos & photos on IO
        val extractedKeyframes = extractVisualKeyframes(context, mediaFiles)

        val clipsMetadata = mediaFiles.mapIndexed { index, file ->
            "Clip #$index: '${file.name}' (${file.type}, maxDuration: ${file.durationSec}s, resolution: ${file.width}x${file.height})"
        }.joinToString("\n")

        val totalAvailableSec = mediaFiles.sumOf { it.durationSec }.coerceAtLeast(4)
        val recommendedDuration = totalAvailableSec.coerceAtMost(targetDurationSec.coerceAtLeast(8))

        val systemPrompt = """
            You are an award-winning Hollywood & Viral Social Media Video Director and Editor.
            Inspect the attached visual keyframes from the user's media clips and create a complete AI video editing timeline based on their prompt.

            Input Media Files Available:
            $clipsMetadata

            Target Aspect Ratio: ${aspectRatio.label}
            Target Total Edited Duration: approximately $recommendedDuration seconds

            User's Video Editing Prompt:
            "$prompt"

            Editing Rules:
            1. Every cut MUST reference a valid clipIndex (0 to ${mediaFiles.size - 1}).
            2. Keep each cut's startTimeSec and endTimeSec strictly between 0 and that clip's maxDuration! Make multiple dynamic cuts if a video clip is long enough or combine all user clips coherently.
            3. Pick playbackSpeed (0.5 to 2.0) and transitions that match the prompt's energy.
            4. Choose the best color grade filterName for the footage and prompt mood.
            5. Create 2 to 5 engaging, synchronized captions (timed sequentially within the edited timeline from 0s onward) reflecting what you actually see in the keyframes and the user's prompt.
            6. Explain your director's rationale referencing the actual scenes visible in the clips.
        """.trimIndent()

        val multimodalContent = content {
            text(systemPrompt)
            for ((label, bitmap) in extractedKeyframes) {
                text("Visual frame from $label:")
                image(bitmap)
            }
        }

        try {
            // Mandated 30-60s timeout for multimodal model inference
            val response = withTimeout(60_000L) {
                model.generateContent(multimodalContent)
            }

            val jsonText = response.text
                ?: throw IllegalStateException("AI model returned an empty response.")

            Log.d(TAG, "AI Edit Plan raw JSON: $jsonText")
            return parseEditPlanJson(jsonText, mediaFiles, aspectRatio)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Video edit generation timed out", e)
            throw IllegalStateException("AI video edit timed out after 60 seconds. Check your internet connection and try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate video edit plan", e)
            val rawMsg = e.localizedMessage ?: e.javaClass.simpleName
            val diagnosticMsg = if (rawMsg.contains("403", ignoreCase = true) ||
                rawMsg.contains("AppCheck", ignoreCase = true) ||
                rawMsg.contains("attestation", ignoreCase = true)
            ) {
                "Firebase App Check rejected the request ($rawMsg). Ensure you are running with internet access and a valid registered build/debug token."
            } else {
                rawMsg
            }
            throw IllegalStateException(diagnosticMsg, e)
        } finally {
            extractedKeyframes.forEach { (_, bmp) ->
                if (!bmp.isRecycled) {
                    bmp.recycle()
                }
            }
        }
    }

    private suspend fun extractVisualKeyframes(
        context: Context,
        mediaFiles: List<MediaFileItem>
    ): List<Pair<String, Bitmap>> = withContext(Dispatchers.IO) {
        val frames = mutableListOf<Pair<String, Bitmap>>()
        val maxTotalFrames = 6
        val perClipLimit = if (mediaFiles.size <= 2) 2 else 1

        for ((index, item) in mediaFiles.withIndex()) {
            if (frames.size >= maxTotalFrames) break
            try {
                if (item.type == MediaType.VIDEO) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, item.uri)
                        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLongOrNull() ?: (item.durationSec * 1000L)

                        val sampleFractions = if (perClipLimit >= 2) listOf(0.20f, 0.65f) else listOf(0.35f)
                        for (fraction in sampleFractions) {
                            if (frames.size >= maxTotalFrames) break
                            val timeUs = (durationMs * fraction * 1000L).toLong().coerceAtLeast(0L)
                            val rawBmp = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            if (rawBmp != null) {
                                val scaled = scaleDownBitmap(rawBmp, 512)
                                if (scaled !== rawBmp) rawBmp.recycle()
                                frames.add("Clip #$index (${item.name} @ ${(durationMs * fraction / 1000f).toInt()}s)" to scaled)
                            }
                        }
                    } finally {
                        try {
                            retriever.release()
                        } catch (_: Exception) {}
                    }
                } else {
                    context.contentResolver.openInputStream(item.uri)?.use { stream ->
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = 2
                        }
                        val rawBmp = BitmapFactory.decodeStream(stream, null, options)
                        if (rawBmp != null) {
                            val scaled = scaleDownBitmap(rawBmp, 512)
                            if (scaled !== rawBmp) rawBmp.recycle()
                            frames.add("Photo #$index (${item.name})" to scaled)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Skipping keyframe extraction for ${item.name}", e)
            }
        }
        frames
    }

    private fun scaleDownBitmap(source: Bitmap, maxSidePx: Int): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= maxSidePx && height <= maxSidePx) return source
        val ratio = maxSidePx.toFloat() / maxOf(width, height).toFloat()
        val targetW = (width * ratio).toInt().coerceAtLeast(1)
        val targetH = (height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetW, targetH, true)
    }

    private fun parseEditPlanJson(
        jsonString: String,
        mediaFiles: List<MediaFileItem>,
        aspectRatio: AspectRatio
    ): VideoEditPlan {
        val root = JSONObject(jsonString)
        val title = root.optString("title", "AI Edited Video").ifBlank { "AI Edited Video" }
        val creativeSummary = root.optString("creativeSummary", "Dynamic AI edit generated from your prompt.")
        val directorsRationale = root.optString("directorsRationale", "Cuts, color grade, and captions tailored to your prompt.")
        val filterNameStr = root.optString("filterName", "CINEMATIC_WARM")
        val musicMood = root.optString("backgroundMusicMood", "Chill Lofi Beats")

        val filter = try {
            VideoFilter.valueOf(filterNameStr.trim().uppercase())
        } catch (_: Exception) {
            VideoFilter.CINEMATIC_WARM
        }

        val cuts = mutableListOf<VideoCut>()
        val cutsArray = root.optJSONArray("cuts")
        if (cutsArray != null) {
            for (i in 0 until cutsArray.length()) {
                val cutObj = cutsArray.getJSONObject(i)
                val clipIdx = cutObj.optInt("clipIndex", i % mediaFiles.size).coerceIn(0, mediaFiles.size - 1)
                val clip = mediaFiles[clipIdx]
                val maxClipSec = clip.durationSec.toFloat().coerceAtLeast(1f)

                var start = cutObj.optDouble("startTimeSec", 0.0).toFloat()
                if (!start.isFinite() || start < 0f) start = 0f
                if (start >= maxClipSec - 0.5f) start = (maxClipSec - 1.5f).coerceAtLeast(0f)

                var rawEnd = cutObj.optDouble("endTimeSec", (start + minOf(4f, maxClipSec)).toDouble()).toFloat()
                if (!rawEnd.isFinite()) rawEnd = maxClipSec
                val end = rawEnd.coerceIn((start + 0.8f).coerceAtMost(maxClipSec), maxClipSec)

                val speed = cutObj.optDouble("playbackSpeed", 1.0).toFloat().let {
                    if (it.isFinite()) it.coerceIn(0.5f, 2.5f) else 1.0f
                }
                val transStr = cutObj.optString("transition", "CROSS_DISSOLVE")
                val trans = try {
                    TransitionType.valueOf(transStr.trim().uppercase())
                } catch (_: Exception) {
                    TransitionType.CROSS_DISSOLVE
                }
                val desc = cutObj.optString("actionDescription", "Scene cut #${i + 1}")

                cuts.add(
                    VideoCut(
                        id = "ai_cut_${i}_${System.currentTimeMillis()}",
                        clipIndex = clipIdx,
                        clipName = clip.name,
                        startTimeSec = start,
                        endTimeSec = end,
                        playbackSpeed = speed,
                        transitionToNext = trans,
                        actionDescription = desc
                    )
                )
            }
        }

        if (cuts.isEmpty()) {
            mediaFiles.forEachIndexed { idx, file ->
                cuts.add(
                    VideoCut(
                        id = "ai_cut_${idx}_${System.currentTimeMillis()}",
                        clipIndex = idx,
                        clipName = file.name,
                        startTimeSec = 0f,
                        endTimeSec = file.durationSec.toFloat().coerceAtLeast(2f),
                        playbackSpeed = 1f,
                        transitionToNext = TransitionType.CROSS_DISSOLVE,
                        actionDescription = "Highlight from ${file.name}"
                    )
                )
            }
        }

        val totalDuration = cuts.sumOf { it.durationSec.toDouble() }.toFloat().coerceAtLeast(2f)

        val captions = mutableListOf<CaptionOverlay>()
        val captionsArray = root.optJSONArray("captions")
        if (captionsArray != null) {
            for (i in 0 until captionsArray.length()) {
                val capObj = captionsArray.getJSONObject(i)
                val text = capObj.optString("text", "").trim()
                if (text.isNotBlank()) {
                    var cStart = capObj.optDouble("startTimeSec", (i * 2.5)).toFloat()
                    if (!cStart.isFinite() || cStart < 0f) cStart = 0f
                    if (cStart >= totalDuration - 0.5f) cStart = (totalDuration - 2f).coerceAtLeast(0f)

                    var cEnd = capObj.optDouble("endTimeSec", (cStart + 2.5)).toFloat()
                    if (!cEnd.isFinite()) cEnd = cStart + 2.5f
                    cEnd = cEnd.coerceIn((cStart + 0.8f).coerceAtMost(totalDuration), totalDuration)

                    val posStr = capObj.optString("position", "BOTTOM")
                    val pos = try {
                        CaptionPosition.valueOf(posStr.trim().uppercase())
                    } catch (_: Exception) {
                        CaptionPosition.BOTTOM
                    }
                    val textColor = capObj.optString("textColorHex", "#FFFFFF").ifBlank { "#FFFFFF" }
                    val hlColor = capObj.optString("highlightColorHex", "#FFDE59").ifBlank { "#FFDE59" }
                    val style = when {
                        hlColor.contains("00F0FF", ignoreCase = true) || filter == VideoFilter.NEON_CYBER -> CaptionStyle.NEON_CYAN
                        hlColor.contains("FF4757", ignoreCase = true) -> CaptionStyle.BOXED_BLACK
                        filter == VideoFilter.NOIR_MONOCHROME -> CaptionStyle.CLASSIC_WHITE
                        else -> CaptionStyle.TIKTOK_YELLOW
                    }

                    captions.add(
                        CaptionOverlay(
                            id = "ai_caption_$i",
                            text = text,
                            startTimeSec = cStart,
                            endTimeSec = cEnd,
                            position = pos,
                            style = style,
                            textColorHex = textColor,
                            highlightColorHex = hlColor
                        )
                    )
                }
            }
        }

        val sfx = mutableListOf<String>()
        val sfxArray = root.optJSONArray("soundEffects")
        if (sfxArray != null) {
            for (i in 0 until sfxArray.length()) {
                val item = sfxArray.optString(i)
                if (item.isNotBlank()) sfx.add(item)
            }
        }

        return VideoEditPlan(
            title = title,
            creativeSummary = creativeSummary,
            directorsRationale = directorsRationale,
            filter = filter,
            aspectRatio = aspectRatio,
            cuts = cuts,
            captions = captions,
            backgroundMusicMood = musicMood,
            soundEffects = sfx,
            estimatedFinalDurationSec = totalDuration,
            isAiGenerated = true
        )
    }
}
