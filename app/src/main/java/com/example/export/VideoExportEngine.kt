package com.example.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import androidx.core.content.FileProvider
import com.example.model.AspectRatio
import com.example.model.CaptionPosition
import com.example.model.ExportQuality
import com.example.model.MediaFileItem
import com.example.model.MediaType
import com.example.model.TransitionType
import com.example.model.VideoEditPlan
import com.example.model.VideoFilter
import com.example.util.FilterMatrixHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.coroutineContext

private const val TAG = "VideoExportEngine"

data class ExportResult(
    val fileUri: Uri?,
    val fileName: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val formattedSize: String,
    val resolutionLabel: String,
    val durationSeconds: Float,
    val galleryUri: Uri? = null
)

class VideoExportEngine {

    suspend fun exportProject(
        context: Context,
        plan: VideoEditPlan,
        mediaFiles: List<MediaFileItem>,
        quality: ExportQuality,
        onProgress: (Float, String) -> Unit
    ): ExportResult = withContext(Dispatchers.IO) {
        if (mediaFiles.isEmpty() || plan.cuts.isEmpty()) {
            throw IllegalStateException("Timeline has no clips to export.")
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val cleanTitle = plan.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(20).ifBlank { "Edit" }
        val fileName = "PromptCut_${cleanTitle}_$timestamp.mp4"

        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()
        val outputFile = File(exportDir, fileName)
        if (outputFile.exists()) outputFile.delete()

        val candidateConfigs = buildResolutionCandidates(plan.aspectRatio, quality)
        var encodedWidth = 1080
        var encodedHeight = 1920
        var encodeSucceeded = false
        var lastError: Throwable? = null

        for ((w, h, bitrate) in candidateConfigs) {
            coroutineContext.ensureActive()
            try {
                encodedWidth = w
                encodedHeight = h
                encodeMp4(
                    context = context,
                    plan = plan,
                    mediaFiles = mediaFiles,
                    outputFile = outputFile,
                    width = w,
                    height = h,
                    bitRate = bitrate,
                    fps = 24,
                    onProgress = onProgress
                )
                encodeSucceeded = true
                break
            } catch (e: Exception) {
                Log.w(TAG, "Encoder failed at ${w}x${h}, trying fallback resolution", e)
                lastError = e
                if (outputFile.exists()) outputFile.delete()
            }
        }

        if (!encodeSucceeded || !outputFile.exists() || outputFile.length() == 0L) {
            throw IllegalStateException(
                "Hardware video encoder failed: ${lastError?.localizedMessage ?: "Unknown error"}",
                lastError
            )
        }

        onProgress(0.96f, "Saving to device gallery...")
        val galleryUri = saveFileToGalleryInternal(context, outputFile, fileName)

        val fileUri: Uri? = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outputFile
            )
        } catch (e: Exception) {
            Uri.fromFile(outputFile)
        }

        val sizeBytes = outputFile.length()
        val sizeFormatted = if (sizeBytes >= 1024 * 1024) {
            String.format(Locale.US, "%.1f MB", sizeBytes / (1024f * 1024f))
        } else {
            String.format(Locale.US, "%.0f KB", (sizeBytes / 1024f).coerceAtLeast(1f))
        }

        val qualityTag = when {
            maxOf(encodedWidth, encodedHeight) >= 3840 -> "4K Ultra HD"
            maxOf(encodedWidth, encodedHeight) >= 1920 -> "Full HD"
            else -> "HD"
        }
        val resolutionText = "$encodedWidth x $encodedHeight ($qualityTag • 24 FPS)"

        onProgress(1.0f, "Export Complete!")

        ExportResult(
            fileUri = fileUri,
            fileName = fileName,
            filePath = outputFile.absolutePath,
            fileSizeBytes = sizeBytes,
            formattedSize = sizeFormatted,
            resolutionLabel = resolutionText,
            durationSeconds = plan.estimatedFinalDurationSec,
            galleryUri = galleryUri
        )
    }

    private fun buildResolutionCandidates(
        aspectRatio: AspectRatio,
        quality: ExportQuality
    ): List<Triple<Int, Int, Int>> {
        val list = mutableListOf<Triple<Int, Int, Int>>()
        when (quality) {
            ExportQuality.ULTRA_HD_4K -> {
                list += when (aspectRatio) {
                    AspectRatio.PORTRAIT_9_16 -> Triple(2160, 3840, 22_000_000)
                    AspectRatio.LANDSCAPE_16_9 -> Triple(3840, 2160, 22_000_000)
                    AspectRatio.SQUARE_1_1 -> Triple(2160, 2160, 18_000_000)
                }
                list += when (aspectRatio) {
                    AspectRatio.PORTRAIT_9_16 -> Triple(1080, 1920, 12_000_000)
                    AspectRatio.LANDSCAPE_16_9 -> Triple(1920, 1080, 12_000_000)
                    AspectRatio.SQUARE_1_1 -> Triple(1080, 1080, 10_000_000)
                }
            }
            ExportQuality.FULL_HD_1080P -> {
                list += when (aspectRatio) {
                    AspectRatio.PORTRAIT_9_16 -> Triple(1080, 1920, 12_000_000)
                    AspectRatio.LANDSCAPE_16_9 -> Triple(1920, 1080, 12_000_000)
                    AspectRatio.SQUARE_1_1 -> Triple(1080, 1080, 10_000_000)
                }
            }
            ExportQuality.HD_720P -> Unit
        }
        // Safe universal fallback
        list += when (aspectRatio) {
            AspectRatio.PORTRAIT_9_16 -> Triple(720, 1280, 6_000_000)
            AspectRatio.LANDSCAPE_16_9 -> Triple(1280, 720, 6_000_000)
            AspectRatio.SQUARE_1_1 -> Triple(720, 720, 5_000_000)
        }
        return list
    }

    private suspend fun encodeMp4(
        context: Context,
        plan: VideoEditPlan,
        mediaFiles: List<MediaFileItem>,
        outputFile: File,
        width: Int,
        height: Int,
        bitRate: Int,
        fps: Int,
        onProgress: (Float, String) -> Unit
    ) {
        val retrievers = mutableMapOf<Int, MediaMetadataRetriever>()
        val cachedImages = mutableMapOf<Int, Bitmap>()

        try {
            mediaFiles.forEachIndexed { index, item ->
                if (item.type == MediaType.VIDEO) {
                    try {
                        val r = MediaMetadataRetriever()
                        r.setDataSource(context, item.uri)
                        retrievers[index] = r
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to open retriever for clip $index", e)
                    }
                } else {
                    try {
                        context.contentResolver.openInputStream(item.uri)?.use { stream ->
                            val opts = BitmapFactory.Options().apply {
                                inPreferredConfig = Bitmap.Config.ARGB_8888
                            }
                            BitmapFactory.decodeStream(stream, null, opts)?.let { bmp ->
                                cachedImages[index] = scaleCenterFit(bmp, maxOf(width, height))
                                if (cachedImages[index] !== bmp) bmp.recycle()
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to decode image for clip $index", e)
                    }
                }
            }

            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = encoder.createInputSurface()
            encoder.start()

            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var trackIndex = -1
            var muxerStarted = false
            val bufferInfo = MediaCodec.BufferInfo()
            val frameDurationUs = 1_000_000L / fps
            var emittedFrameCount = 0L

            val colorMatrix = FilterMatrixHelper.buildColorMatrix(plan.filter, plan.filterIntensity)
            val framePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(colorMatrix)
            }

            fun drainEncoder(endOfStream: Boolean) {
                if (endOfStream) {
                    encoder.signalEndOfInputStream()
                }
                while (true) {
                    val outIndex = encoder.dequeueOutputBuffer(bufferInfo, if (endOfStream) 10_000L else 0L)
                    when {
                        outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!endOfStream) break
                        }
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (muxerStarted) throw RuntimeException("Output format changed twice")
                            val newFormat = encoder.outputFormat
                            trackIndex = muxer.addTrack(newFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        outIndex >= 0 -> {
                            val encodedData = encoder.getOutputBuffer(outIndex)
                                ?: throw RuntimeException("Encoder output buffer was null")
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                bufferInfo.size = 0
                            }
                            if (bufferInfo.size > 0) {
                                if (!muxerStarted) {
                                    val newFormat = encoder.outputFormat
                                    trackIndex = muxer.addTrack(newFormat)
                                    muxer.start()
                                    muxerStarted = true
                                }
                                encodedData.position(bufferInfo.offset)
                                encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                bufferInfo.presentationTimeUs = emittedFrameCount * frameDurationUs
                                muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                                emittedFrameCount++
                            }
                            encoder.releaseOutputBuffer(outIndex, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                break
                            }
                        }
                    }
                }
            }

            try {
                val totalDuration = plan.cuts.sumOf { it.durationSec.toDouble() }.toFloat().coerceAtLeast(1f)
                val totalFrames = (totalDuration * fps).toInt().coerceAtLeast(fps)

                var lastClipIndex = -1
                var lastSourceBucketMs = -1L
                var lastVideoBitmap: Bitmap? = null

                for (frameIdx in 0 until totalFrames) {
                    coroutineContext.ensureActive()
                    val timelineSec = frameIdx.toFloat() / fps.toFloat()

                    // Locate active cut and relative offset within cut
                    var accumSec = 0f
                    var currentCut = plan.cuts.last()
                    var cutLocalTimelineSec = 0f
                    for (cut in plan.cuts) {
                        val dur = cut.durationSec
                        if (timelineSec >= accumSec && timelineSec < accumSec + dur) {
                            currentCut = cut
                            cutLocalTimelineSec = timelineSec - accumSec
                            break
                        }
                        accumSec += dur
                    }

                    val clipIdx = currentCut.clipIndex.coerceIn(0, mediaFiles.size - 1)
                    val clipItem = mediaFiles[clipIdx]

                    // Fetch frame bitmap
                    val sourceBitmap: Bitmap? = if (clipItem.type == MediaType.IMAGE) {
                        cachedImages[clipIdx]
                    } else {
                        val sourceSec = (currentCut.startTimeSec + cutLocalTimelineSec * currentCut.playbackSpeed)
                            .coerceIn(0f, clipItem.durationSec.toFloat().coerceAtLeast(1f))
                        val sourceMs = (sourceSec * 1000f).toLong()
                        // Reuse frame if within ~40ms bucket on same clip to keep export snappy
                        val bucketMs = (sourceMs / 40L) * 40L
                        if (clipIdx != lastClipIndex || bucketMs != lastSourceBucketMs || lastVideoBitmap == null) {
                            val retriever = retrievers[clipIdx]
                            val fetched = retriever?.let { r ->
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                                    r.getScaledFrameAtTime(
                                        sourceMs * 1000L,
                                        MediaMetadataRetriever.OPTION_CLOSEST,
                                        width,
                                        height
                                    ) ?: r.getFrameAtTime(sourceMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                } else {
                                    r.getFrameAtTime(sourceMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                }
                            }
                            if (fetched != null) {
                                lastVideoBitmap?.recycle()
                                lastVideoBitmap = fetched
                                lastClipIndex = clipIdx
                                lastSourceBucketMs = bucketMs
                            }
                        }
                        lastVideoBitmap
                    }

                    val canvas: Canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        inputSurface.lockHardwareCanvas()
                    } else {
                        inputSurface.lockCanvas(null)
                    }

                    try {
                        canvas.drawColor(Color.BLACK)

                        // Compute transition progress (first 0.45s of cut)
                        val transProgress = (cutLocalTimelineSec / 0.45f).coerceIn(0f, 1f)
                        val isTransitioning = cutLocalTimelineSec < 0.45f && frameIdx > 0

                        if (sourceBitmap != null && !sourceBitmap.isRecycled) {
                            drawTransformedFrame(
                                canvas = canvas,
                                bitmap = sourceBitmap,
                                targetW = width,
                                targetH = height,
                                paint = framePaint,
                                cutDurationSec = currentCut.durationSec,
                                localSec = cutLocalTimelineSec,
                                isImage = clipItem.type == MediaType.IMAGE,
                                transition = currentCut.transitionToNext,
                                transProgress = transProgress,
                                isTransitioning = isTransitioning
                            )
                        }

                        // Filter-specific subtle overlays
                        drawFilterAtmosphere(canvas, plan.filter, width, height, frameIdx, fps)

                        // Burn active captions
                        val activeCaption = plan.captions.firstOrNull {
                            timelineSec >= it.startTimeSec && timelineSec <= it.endTimeSec
                        }
                        if (activeCaption != null) {
                            drawCaption(canvas, activeCaption.text, activeCaption.position, activeCaption.highlightColorHex, width, height)
                        }
                    } finally {
                        inputSurface.unlockCanvasAndPost(canvas)
                    }

                    drainEncoder(false)

                    if (frameIdx % 4 == 0 || frameIdx == totalFrames - 1) {
                        val ratio = (frameIdx + 1).toFloat() / totalFrames.toFloat()
                        val stage = when {
                            ratio < 0.3f -> "Encoding scenes & color grade (${width}x${height})..."
                            ratio < 0.7f -> "Rendering transitions & captions..."
                            else -> "Finalizing MP4 container..."
                        }
                        onProgress(0.08f + ratio * 0.85f, stage)
                    }
                }

                lastVideoBitmap?.recycle()
                drainEncoder(true)
            } finally {
                try { encoder.stop() } catch (_: Exception) {}
                try { encoder.release() } catch (_: Exception) {}
                try { inputSurface.release() } catch (_: Exception) {}
                try { if (muxerStarted) muxer.stop() } catch (_: Exception) {}
                try { muxer.release() } catch (_: Exception) {}
            }
        } finally {
            retrievers.values.forEach { try { it.release() } catch (_: Exception) {} }
            cachedImages.values.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    private fun drawTransformedFrame(
        canvas: Canvas,
        bitmap: Bitmap,
        targetW: Int,
        targetH: Int,
        paint: Paint,
        cutDurationSec: Float,
        localSec: Float,
        isImage: Boolean,
        transition: TransitionType,
        transProgress: Float,
        isTransitioning: Boolean
    ) {
        val bw = bitmap.width.toFloat().coerceAtLeast(1f)
        val bh = bitmap.height.toFloat().coerceAtLeast(1f)
        val baseScale = maxOf(targetW / bw, targetH / bh)

        var extraScale = 1f
        if (isImage) {
            // Subtle Ken Burns zoom on photos
            val progress = (localSec / cutDurationSec.coerceAtLeast(1f)).coerceIn(0f, 1f)
            extraScale += progress * 0.08f
        }

        var translateX = 0f
        var fadeOverlayAlpha = 0

        if (isTransitioning) {
            when (transition) {
                TransitionType.ZOOM_IN -> extraScale *= (1.18f - 0.18f * transProgress)
                TransitionType.SLIDE_LEFT -> translateX = targetW * 0.25f * (1f - transProgress)
                TransitionType.FADE_BLACK -> fadeOverlayAlpha = ((1f - transProgress) * 210).toInt().coerceIn(0, 255)
                TransitionType.CROSS_DISSOLVE -> fadeOverlayAlpha = ((1f - transProgress) * 120).toInt().coerceIn(0, 255)
                TransitionType.GLITCH_TRANSITION -> {
                    translateX = if ((transProgress * 10).toInt() % 2 == 0) 24f else -24f
                    extraScale *= 1.04f
                }
                TransitionType.CUT -> Unit
            }
        }

        val finalScale = baseScale * extraScale
        val scaledW = bw * finalScale
        val scaledH = bh * finalScale
        val dx = (targetW - scaledW) / 2f + translateX
        val dy = (targetH - scaledH) / 2f

        val matrix = Matrix().apply {
            postScale(finalScale, finalScale)
            postTranslate(dx, dy)
        }
        canvas.drawBitmap(bitmap, matrix, paint)

        if (fadeOverlayAlpha > 0) {
            canvas.drawColor(Color.argb(fadeOverlayAlpha, 0, 0, 0))
        }
    }

    private fun drawFilterAtmosphere(
        canvas: Canvas,
        filter: VideoFilter,
        width: Int,
        height: Int,
        frameIdx: Int,
        fps: Int
    ) {
        when (filter) {
            VideoFilter.RETRO_VHS -> {
                canvas.drawColor(Color.argb(24, 255, 0, 85))
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(215, 74, 222, 128)
                    textSize = width * 0.032f
                    typeface = Typeface.MONOSPACE
                    isFakeBoldText = true
                }
                val sec = frameIdx / fps
                canvas.drawText(String.format(Locale.US, "REC ● 00:%02d:%02d", sec / 60, sec % 60), width * 0.05f, height * 0.06f, p)
            }
            VideoFilter.NEON_CYBER -> {
                val topPaint = Paint().apply { color = Color.argb(26, 0, 255, 255) }
                canvas.drawRect(0f, 0f, width.toFloat(), height * 0.35f, topPaint)
                val botPaint = Paint().apply { color = Color.argb(34, 255, 0, 128) }
                canvas.drawRect(0f, height * 0.65f, width.toFloat(), height.toFloat(), botPaint)
            }
            else -> Unit
        }
    }

    private fun drawCaption(
        canvas: Canvas,
        text: String,
        position: CaptionPosition,
        highlightHex: String,
        width: Int,
        height: Int
    ) {
        val accentColor = try {
            Color.parseColor(highlightHex)
        } catch (_: Exception) {
            Color.parseColor("#FFDE59")
        }

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = (width * 0.048f).coerceIn(28f, 96f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }

        val maxTextWidth = (width * 0.80f).toInt().coerceAtLeast(100)
        @Suppress("DEPRECATION")
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, maxTextWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(4f, 1.05f)
            .build()

        var maxLineWidth = 0f
        for (i in 0 until layout.lineCount) {
            maxLineWidth = maxOf(maxLineWidth, layout.getLineWidth(i))
        }

        val padH = width * 0.036f
        val padV = width * 0.02f
        val boxW = (maxLineWidth + padH * 2f).coerceAtMost(width * 0.88f)
        val boxH = layout.height + padV * 2f
        val left = (width - boxW) / 2f
        val top = when (position) {
            CaptionPosition.TOP -> height * 0.10f
            CaptionPosition.CENTER -> (height - boxH) / 2f
            CaptionPosition.BOTTOM -> height * 0.82f - boxH
        }

        val rect = RectF(left, top, left + boxW, top + boxH)
        val radius = width * 0.022f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(215, 10, 12, 20)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
            style = Paint.Style.STROKE
            strokeWidth = (width * 0.004f).coerceAtLeast(2f)
        }

        canvas.drawRoundRect(rect, radius, radius, bgPaint)
        canvas.drawRoundRect(rect, radius, radius, borderPaint)

        canvas.save()
        canvas.translate((width - maxTextWidth) / 2f, top + padV)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun scaleCenterFit(source: Bitmap, maxDim: Int): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= maxDim && h <= maxDim) return source
        val scale = maxDim.toFloat() / maxOf(w, h).toFloat()
        return Bitmap.createScaledBitmap(source, (w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1), true)
    }

    private fun saveFileToGalleryInternal(context: Context, sourceFile: File, fileName: String): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/PromptCut")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val itemUri = resolver.insert(collection, values) ?: return null
                resolver.openOutputStream(itemUri)?.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out)
                    }
                }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
                itemUri
            } else {
                val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                val appDir = File(moviesDir, "PromptCut")
                if (!appDir.exists()) appDir.mkdirs()
                val dest = File(appDir, fileName)
                FileInputStream(sourceFile).use { input ->
                    dest.outputStream().use { out ->
                        input.copyTo(out)
                    }
                }
                Uri.fromFile(dest)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gallery save skipped/failed, cached FileProvider URI remains available", e)
            null
        }
    }

    fun previewExportedVideo(context: Context, result: ExportResult) {
        val uri = result.galleryUri ?: result.fileUri ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Play Exported Video")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun shareExportedVideo(context: Context, result: ExportResult) {
        val uri = result.fileUri ?: result.galleryUri ?: return
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "AI Edited Video: ${result.fileName}")
            putExtra(Intent.EXTRA_TEXT, "Edited with PromptCut AI Studio in ${result.resolutionLabel}!")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Video via")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
