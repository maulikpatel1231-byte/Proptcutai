package com.example

import com.example.model.AspectRatio
import com.example.model.TransitionType
import com.example.model.VideoCut
import com.example.model.VideoFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun videoFilter_enumProperties() {
        val warmFilter = VideoFilter.CINEMATIC_WARM
        assertTrue(warmFilter.saturation > 1.0f)
        val noir = VideoFilter.NOIR_MONOCHROME
        assertEquals(0.0f, noir.saturation, 0.001f)
    }

    @Test
    fun aspectRatio_calculation() {
        val portrait = AspectRatio.PORTRAIT_9_16
        assertEquals(9f, portrait.ratioWidth, 0.01f)
        assertEquals(16f, portrait.ratioHeight, 0.01f)
        assertTrue(portrait.ratioWidth < portrait.ratioHeight)
    }

    @Test
    fun videoCut_durationCalculation() {
        val cut = VideoCut(
            id = "cut_test",
            clipIndex = 0,
            clipName = "Clip_1.mp4",
            startTimeSec = 2f,
            endTimeSec = 6f,
            playbackSpeed = 2f,
            transitionToNext = TransitionType.ZOOM_IN,
            actionDescription = "Test cut"
        )
        // (6 - 2) / 2 = 2.0s
        assertEquals(2.0f, cut.durationSec, 0.01f)
    }
}
