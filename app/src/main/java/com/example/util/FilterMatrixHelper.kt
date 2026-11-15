package com.example.util

import android.graphics.ColorMatrix
import com.example.model.VideoFilter

object FilterMatrixHelper {

    fun buildColorMatrix(filter: VideoFilter, intensity: Float): ColorMatrix {
        val t = intensity.coerceIn(0f, 1f)
        val cm = ColorMatrix()

        // 1. Saturation
        val sat = 1.0f + (filter.saturation - 1.0f) * t
        cm.setSaturation(sat)

        // 2. Contrast + Brightness + Warmth/Cool Tint
        val scale = 1.0f + (filter.contrast - 1.0f) * t
        val brightnessOffset = filter.brightness * t * 255f
        val translate = (-0.5f * scale + 0.5f) * 255f + brightnessOffset

        val warmth = filter.warmthHue * t
        val rBoost = 1.0f + (warmth * 0.22f)
        val bBoost = 1.0f - (warmth * 0.22f)
        val gBoost = if (filter == VideoFilter.NEON_CYBER) 1.0f - (0.06f * t) else 1.0f

        val adjustMatrix = ColorMatrix(
            floatArrayOf(
                scale * rBoost, 0f, 0f, 0f, translate,
                0f, scale * gBoost, 0f, 0f, translate,
                0f, 0f, scale * bBoost, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.postConcat(adjustMatrix)
        return cm
    }
}
