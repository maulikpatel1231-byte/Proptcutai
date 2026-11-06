package com.example.ai

import android.util.Log
import com.example.model.AspectRatio
import com.example.model.CaptionOverlay
import com.example.model.CaptionPosition
import com.example.model.MediaFileItem
import com.example.model.TransitionType
import com.example.model.VideoCut
import com.example.model.VideoEditPlan
import com.example.model.VideoFilter
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "AiVideoDirector"

class AiVideoDirector {

    // Structured JSON schema definition using Schema.obj (required by Firebase AI Logic Kotlin SDK)
    private val cutSchema = Schema.obj(
        properties = mapOf(
            "clipIndex" to Schema.integer("Zero-based index of media file"),
            "clipName" to Schema.string("Name or title of the clip"),
            "startTimeSec" to Schema.double("Start trim position in seconds"),
            "endTimeSec" to Schema.double("End trim position in seconds"),
            "playbackSpeed" to Schema.double("Speed multiplier, e.g. 0.5, 1.0, 1.5, 2.0"),
            "transition" to Schema.string("Transition to next clip: CUT, CROSS_DISSOLVE, ZOOM_IN, SLIDE_LEFT, GLITCH_TRANSITION, FADE_BLACK"),
            "actionDescription" to Schema.string("Brief description of what happens in this cut")
        )
    )

    private val captionSchema = Schema.obj(
        properties = mapOf(
            "text" to Schema.string("Caption or subtitle text to display on screen"),
            "startTimeSec" to Schema.double("Timestamp in seconds when caption appears"),
            "endTimeSec" to Schema.double("Timestamp in seconds when caption disappears"),
            "position" to Schema.string("Screen position: TOP, CENTER, BOTTOM"),
            "textColorHex" to Schema.string("Hex color code for text, e.g. #FFFFFF"),
            "highlightColorHex" to Schema.string("Hex accent highlight color, e.g. #FFDE59 or #00F0FF")
        )
    )

    private val editPlanSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string("Catchy creative project title"),
            "creativeSummary" to Schema.string("Summary of video edit style and pacing"),
            "directorsRationale" to Schema.string("Professional explanation of why these cuts, color grade, and captions were chosen"),
            "filterName" to Schema.string("Visual filter: ORIGINAL, CINEMATIC_WARM, RETRO_VHS, NEON_CYBER, NOIR_MONOCHROME, VIVID_POP, PASTEL_DREAM"),
            "backgroundMusicMood" to Schema.string("Suggested background audio mood or track genre"),
            "cuts" to Schema.array(cutSchema, "List of sequence cuts for the video timeline"),
            "captions" to Schema.array(captionSchema, "List of synchronized caption/text overlays"),
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
        prompt: String,
        mediaFiles: List<MediaFileItem>,
        aspectRatio: AspectRatio,
        targetDurationSec: Int = 15
    ): VideoEditPlan {
        if (mediaFiles.isEmpty()) {
            throw IllegalArgumentException("At least one video or photo file must be provided.")
        }

        val clipsMetadata = mediaFiles.mapIndexed { index, file ->
            "Clip #$index: '${file.name}' (${file.type}, duration: ${file.durationSec}s, ${file.resolutionLabel}) - ${file.description.ifBlank { "User uploaded media clip" }}"
        }.joinToString("\n")

        val systemPrompt = """
            You are an award-winning Hollywood and Viral Social Media Video Director and Editor.
            The user wants you to edit their video according to their natural language prompt instructions.
            
            Input Media Files Available:
            $clipsMetadata
            
            Target Aspect Ratio: ${aspectRatio.label}
            Target Total Duration: approximately $targetDurationSec seconds
            
            User's Video Editing Prompt:
            "$prompt"
            
            Instructions:
            1. Formulate a sequence of cuts (clipIndex, startTimeSec, endTimeSec, playbackSpeed, transition, actionDescription) that fulfills the user's creative vision.
            2. Choose the best visual color grade filter from: ORIGINAL, CINEMATIC_WARM, RETRO_VHS, NEON_CYBER, NOIR_MONOCHROME, VIVID_POP, PASTEL_DREAM.
            3. Generate engaging, synchronized captions/subtitles that pop on screen at specific timestamps (matching the tone of the prompt, e.g. viral hooks, poetic cinema lines, or informative titles).
            4. Recommend an audio soundtrack mood and sound effects.
            5. Provide a clear director's rationale explaining your editing choices.
        """.trimIndent()

        try {
            // Mandated 30-60s timeout for model inference
            val response = withTimeout(60_000L) {
                model.generateContent(systemPrompt)
            }

            val jsonText = response.text
                ?: throw IllegalStateException("AI model returned an empty response.")

            Log.d(TAG, "AI Edit Plan raw JSON: $jsonText")
            return parseEditPlanJson(jsonText, mediaFiles, aspectRatio)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Video edit generation timed out", e)
            throw IllegalStateException("AI video edit planning timed out after 60 seconds. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate video edit plan", e)
            throw e
        }
    }

    private fun parseEditPlanJson(
        jsonString: String,
        mediaFiles: List<MediaFileItem>,
        aspectRatio: AspectRatio
    ): VideoEditPlan {
        val root = JSONObject(jsonString)
        val title = root.optString("title", "AI Edited Video")
        val creativeSummary = root.optString("creativeSummary", "Dynamic edit generated from prompt")
        val directorsRationale = root.optString("directorsRationale", "Cuts and filters optimized for visual rhythm.")
        val filterNameStr = root.optString("filterName", "CINEMATIC_WARM")
        val musicMood = root.optString("backgroundMusicMood", "Chill Lofi Beats")

        val filter = try {
            VideoFilter.valueOf(filterNameStr.uppercase())
        } catch (_: Exception) {
            VideoFilter.CINEMATIC_WARM
        }

        val cuts = mutableListOf<VideoCut>()
        val cutsArray = root.optJSONArray("cuts")
        if (cutsArray != null) {
            for (i in 0 until cutsArray.length()) {
                val cutObj = cutsArray.getJSONObject(i)
                val clipIdx = cutObj.optInt("clipIndex", 0).coerceIn(0, mediaFiles.size - 1)
                val clip = mediaFiles[clipIdx]
                val start = cutObj.optDouble("startTimeSec", 0.0).toFloat().coerceAtLeast(0f)
                val rawEnd = cutObj.optDouble("endTimeSec", (start + 3.0)).toFloat()
                val end = if (rawEnd <= start) start + 3f else rawEnd
                val speed = cutObj.optDouble("playbackSpeed", 1.0).toFloat().coerceIn(0.25f, 3.0f)
                val transStr = cutObj.optString("transition", "CROSS_DISSOLVE")
                val trans = try {
                    TransitionType.valueOf(transStr.uppercase())
                } catch (_: Exception) {
                    TransitionType.CROSS_DISSOLVE
                }
                val desc = cutObj.optString("actionDescription", "Scene cut #$i")

                cuts.add(
                    VideoCut(
                        id = "cut_$i",
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

        // If no cuts were generated, build fallback cuts from available media
        if (cuts.isEmpty()) {
            mediaFiles.forEachIndexed { idx, file ->
                cuts.add(
                    VideoCut(
                        id = "cut_$idx",
                        clipIndex = idx,
                        clipName = file.name,
                        startTimeSec = 0f,
                        endTimeSec = 4f,
                        playbackSpeed = 1f,
                        transitionToNext = TransitionType.CROSS_DISSOLVE,
                        actionDescription = "Highlight from ${file.name}"
                    )
                )
            }
        }

        val captions = mutableListOf<CaptionOverlay>()
        val captionsArray = root.optJSONArray("captions")
        if (captionsArray != null) {
            for (i in 0 until captionsArray.length()) {
                val capObj = captionsArray.getJSONObject(i)
                val text = capObj.optString("text", "")
                if (text.isNotBlank()) {
                    val cStart = capObj.optDouble("startTimeSec", 0.0).toFloat().coerceAtLeast(0f)
                    val cEnd = capObj.optDouble("endTimeSec", (cStart + 2.5)).toFloat().coerceAtLeast(cStart + 0.8f)
                    val posStr = capObj.optString("position", "BOTTOM")
                    val pos = try {
                        CaptionPosition.valueOf(posStr.uppercase())
                    } catch (_: Exception) {
                        CaptionPosition.BOTTOM
                    }
                    val textColor = capObj.optString("textColorHex", "#FFFFFF")
                    val hlColor = capObj.optString("highlightColorHex", "#FFDE59")

                    captions.add(
                        CaptionOverlay(
                            id = "caption_$i",
                            text = text,
                            startTimeSec = cStart,
                            endTimeSec = cEnd,
                            position = pos,
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
                sfx.add(sfxArray.getString(i))
            }
        }

        val totalDuration = cuts.sumOf { it.durationSec.toDouble() }.toFloat().coerceAtLeast(3f)

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
            estimatedFinalDurationSec = totalDuration
        )
    }
}
